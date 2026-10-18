package az.yolcu.sim;

import az.yolcu.config.YolcuProperties;
import az.yolcu.geo.GeoStore;
import az.yolcu.geo.Polyline;
import az.yolcu.geo.StopService;
import az.yolcu.sim.dto.BusPosition;
import az.yolcu.stream.StreamEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * BE-3.1/3.2/3.7: virtual buses moving along the real trajectories (data/processed/routes.geojson) at route speed,
 * occupancy random walk around a base that follows the route_config interval. All in memory.
 */
@Service
public class SimulationService {
    static final double TICK_SECONDS = 2;
    // shortcut: buses all run at route speed, so spacing never changes on its own; 88 starts with 10-BB-511 2 min
    // behind 10-BB-482 so bunching (design #p-occ) is visible from boot. Replace when speeds vary per bus.
    static final String DEMO_BUNCH_ROUTE = "88";
    static final double DEMO_BUNCH_GAP_MIN = 2;
    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    static final class SimBus {
        final String bort, type;
        final int capacity;
        double s, occ;
        int hold; // ticks left standing still (driver told to wait)

        SimBus(String bort, String type, int capacity, double s, double occ) {
            this.bort = bort;
            this.type = type;
            this.capacity = capacity;
            this.s = s;
            this.occ = occ;
        }
    }

    /** Two consecutive buses of a route: back drives gapMin minutes behind front. */
    public record Gap(String route, BusPosition front, BusPosition back, double gapMin) {}

    static final class SimRoute {
        final String route;
        final Polyline line;
        final double speedKmh;
        double base;
        int interval, busCount = -1;
        List<SimBus> buses = List.of();

        SimRoute(String route, Polyline line, double speedKmh, double base) {
            this.route = route;
            this.line = line;
            this.speedKmh = speedKmh;
            this.base = base;
        }
    }

    private final Map<String, JsonNode> params = new HashMap<>(); // mocks/sim_routes.json by route
    private final Map<String, Integer> seedInterval = new HashMap<>(); // mocks/route_config.json: baseOccupancy is at this interval
    private final Map<String, SimRoute> routes = new LinkedHashMap<>();
    private final Random rnd = new Random(88);
    private final GeoStore geo;
    private final StopService stops;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private volatile List<BusPosition> snapshot = List.of();

    public SimulationService(YolcuProperties props, ObjectMapper mapper, GeoStore geo, StopService stops, JdbcTemplate jdbc,
                             ApplicationEventPublisher events) throws IOException {
        this.geo = geo;
        this.stops = stops;
        this.jdbc = jdbc;
        this.events = events;
        for (var r : mapper.readTree(props.mocksPath().resolve("sim_routes.json").toFile()).path("routes")) {
            params.put(r.path("route").asText(), r);
        }
        for (var r : mapper.readTree(props.mocksPath().resolve("route_config.json").toFile())) {
            seedInterval.put(r.path("route").asText(), r.path("intervalMin").asInt());
        }
    }

    @Scheduled(fixedRate = 2_000, initialDelay = 2_000)
    public void tick() {
        events.publishEvent(StreamEvent.all("bus.positions", step()));
    }

    /** One simulation step: sync route_config, move buses, random-walk occupancy. Returns the new snapshot. */
    public synchronized List<BusPosition> step() {
        syncConfig();
        var out = new ArrayList<BusPosition>();
        for (var r : routes.values()) {
            double ds = r.speedKmh / 3.6 * TICK_SECONDS;
            for (var b : r.buses) {
                if (b.hold > 0) b.hold--;
                else b.s = r.line.wrap(b.s + ds);
                b.occ = clamp(b.occ + (r.base - b.occ) * 0.2 + (rnd.nextDouble() * 8 - 4), 8, 100);
                var p = r.line.at(b.s);
                out.add(new BusPosition(b.bort, r.route, round5(p[0]), round5(p[1]), (int) Math.round(p[2]) % 360,
                        (int) Math.round(b.occ), b.capacity, b.type));
            }
        }
        return snapshot = List.copyOf(out);
    }

    public List<BusPosition> buses(String route) {
        return route == null || route.isBlank() ? snapshot : snapshot.stream().filter(b -> b.route().equals(route)).toList();
    }

    /** Fleet-weighted mean route speed (km/h); 0 before the first tick. */
    public synchronized double avgSpeedKmh() {
        return routes.values().stream().flatMap(r -> r.buses.stream().map(b -> r.speedKmh))
                .mapToDouble(Double::doubleValue).average().orElse(0);
    }

    /**
     * BE-3.7: minutes until the nearest bus of the route that is approaching the point reaches it
     * (distance along the line / route speed). Null if the route is not simulated or has no buses.
     */
    public synchronized Integer etaMin(String route, double lat, double lon) {
        var r = routes.get(route);
        if (r == null || r.buses.isEmpty()) return null;
        double target = r.line.nearest(lat, lon)[1];
        double best = r.buses.stream().mapToDouble(b -> r.line.wrap(target - b.s)).min().orElseThrow();
        return (int) Math.ceil(best / (r.speedKmh / 3.6) / 60);
    }

    /** mocks/sim_routes.json entry (type, operator code, speedKmh, baseOccupancy); missing node if unknown. */
    public JsonNode routeParams(String route) { return params.getOrDefault(route, MissingNode.getInstance()); }

    /** BE-8.3: consecutive bus pairs (along the line, by route speed) closer than maxMin minutes. */
    public synchronized List<Gap> gaps(double maxMin) {
        var pos = new HashMap<String, BusPosition>();
        snapshot.forEach(p -> pos.put(p.bort(), p));
        var out = new ArrayList<Gap>();
        for (var r : routes.values()) {
            if (r.buses.size() < 2) continue;
            var sorted = r.buses.stream().sorted(Comparator.comparingDouble(b -> b.s)).toList();
            for (int i = 0; i < sorted.size(); i++) {
                var back = sorted.get(i);
                var front = sorted.get((i + 1) % sorted.size());
                double gapMin = r.line.wrap(front.s - back.s) / (r.speedKmh / 3.6) / 60;
                if (gapMin < maxMin && pos.containsKey(front.bort) && pos.containsKey(back.bort)) {
                    out.add(new Gap(r.route, pos.get(front.bort), pos.get(back.bort), gapMin));
                }
            }
        }
        return out;
    }

    /** Bus stands still for the given minutes (dispatcher told the driver to wait). False if not simulated. */
    public synchronized boolean hold(String route, String bort, double minutes) {
        var r = routes.get(route);
        if (r == null) return false;
        for (var b : r.buses) {
            if (b.bort.equals(bort)) {
                b.hold = (int) Math.round(minutes * 60 / TICK_SECONDS);
                return true;
            }
        }
        return false;
    }

    /** Demo overload: route base occupancy (and every bus) set to pct; the random walk then stays around it. */
    public synchronized boolean overload(String route, double pct) {
        var r = routes.get(route);
        if (r == null) return false;
        r.base = clamp(pct, 8, 100);
        r.buses.forEach(b -> b.occ = r.base);
        return true;
    }

    /**
     * Demo bunching: the second bort of the route (10-BB-511 on 88) is put DEMO_BUNCH_GAP_MIN behind the first one
     * (10-BB-482) and both stop holding, so the pair is bunched right now regardless of what happened before.
     */
    public synchronized boolean bunch(String route) {
        var r = routes.get(route);
        if (r == null || r.buses.size() < 2) return false;
        var front = r.buses.get(0);
        var back = r.buses.get(1);
        back.s = r.line.wrap(front.s - DEMO_BUNCH_GAP_MIN * 60 * r.speedKmh / 3.6);
        front.hold = back.hold = 0;
        return true;
    }

    /** Demo reset: forget all routes; the next step rebuilds them from route_config (spacing, base occupancy as at boot). */
    public synchronized List<BusPosition> reset() {
        routes.clear();
        return step();
    }

    private void syncConfig() {
        for (var row : jdbc.queryForList("select route, interval_min, bus_count from route_config order by route")) {
            var route = (String) row.get("route");
            int interval = ((Number) row.get("interval_min")).intValue(), busCount = ((Number) row.get("bus_count")).intValue();
            var r = routes.computeIfAbsent(route, this::build);
            if (r == null) continue;
            int from = r.interval > 0 ? r.interval : seedInterval.getOrDefault(route, interval); // after a restart route_config may differ from the seed
            if (from != interval) {
                // same formula as IntervalEngine: shorter interval → proportionally fewer people per bus
                r.base = clamp(r.base * interval / from, 8, 100);
                log.info("Sim {}: interval {} → {} min, base occupancy now {}%", route, from, interval, Math.round(r.base));
            }
            r.interval = interval;
            if (r.busCount != busCount) spawn(r, busCount);
        }
    }

    private void spawn(SimRoute r, int busCount) {
        var borts = jdbc.queryForList("select bort, type, capacity from buses where route = ? and status = 'LINE' order by id",
                r.route);
        int n = Math.min(busCount, borts.size());
        if (n < busCount) log.warn("Sim {}: route_config wants {} buses, buses table has {} on LINE → simulating {}",
                r.route, busCount, borts.size(), n);
        var buses = new ArrayList<SimBus>();
        double len = r.line.length(), gap = DEMO_BUNCH_GAP_MIN * 60 * r.speedKmh / 3.6;
        boolean bunch = r.route.equals(DEMO_BUNCH_ROUTE) && n >= 2 && gap < len / n;
        for (int i = 0; i < n; i++) {
            var b = borts.get(i);
            // bunched: bus 1 at 0, bus 0 gap ahead of it, the rest evenly over the remaining loop
            double s = !bunch ? len * i / n : i == 0 ? gap : i == 1 ? 0 : gap + (i - 1) * (len - gap) / (n - 1);
            buses.add(new SimBus((String) b.get("bort"), (String) b.get("type"), ((Number) b.get("capacity")).intValue(),
                    s, clamp(r.base + rnd.nextDouble() * 12 - 6, 8, 100)));
        }
        r.buses = buses;
        r.busCount = busCount;
    }

    private SimRoute build(String route) {
        var p = params.get(route);
        if (p == null) {
            log.warn("Sim {}: no entry in mocks/sim_routes.json (speed/base), not simulated", route);
            return null;
        }
        var parts = geo.routeLines().getOrDefault(route, List.of());
        Polyline line;
        if (!parts.isEmpty()) {
            line = parts.stream().max(Comparator.comparingDouble(Polyline::length)).orElseThrow();
            log.info("Sim {}: real trajectory, {} pts, {} km, closed={}", route, line.size(),
                    Math.round(line.length() / 100) / 10.0, line.closed());
        } else {
            // fallback: approximate stop sequence out and back (closed loop)
            var pts = new ArrayList<double[]>();
            p.path("stops").forEach(id -> stops.byId(id.asText()).ifPresent(s -> pts.add(new double[]{s.lat(), s.lon()})));
            if (pts.size() < 2) {
                log.warn("Sim {}: no trajectory and < 2 known stops, not simulated", route);
                return null;
            }
            for (int i = pts.size() - 2; i >= 0; i--) pts.add(pts.get(i));
            line = new Polyline(pts);
            log.warn("Sim {}: trajectory missing in routes.geojson, using mocks/sim_routes.json stops", route);
        }
        return new SimRoute(route, line, p.path("speedKmh").asDouble(), p.path("baseOccupancy").asDouble());
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private static double round5(double v) { return Math.round(v * 1e5) / 1e5; }
}
