package az.yolcu.sim;

import az.yolcu.config.YolcuProperties;
import az.yolcu.geo.GeoStore;
import az.yolcu.geo.Polyline;
import az.yolcu.geo.StopService;
import az.yolcu.sim.dto.BusPosition;
import az.yolcu.stream.StreamEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final Logger log = LoggerFactory.getLogger(SimulationService.class);

    static final class SimBus {
        final String bort, type;
        final int capacity;
        double s, occ;

        SimBus(String bort, String type, int capacity, double s, double occ) {
            this.bort = bort;
            this.type = type;
            this.capacity = capacity;
            this.s = s;
            this.occ = occ;
        }
    }

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
                b.s = r.line.wrap(b.s + ds);
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

    private void syncConfig() {
        for (var row : jdbc.queryForList("select route, interval_min, bus_count from route_config order by route")) {
            var route = (String) row.get("route");
            int interval = ((Number) row.get("interval_min")).intValue(), busCount = ((Number) row.get("bus_count")).intValue();
            var r = routes.computeIfAbsent(route, this::build);
            if (r == null) continue;
            if (r.interval > 0 && r.interval != interval) {
                // same formula as IntervalEngine: shorter interval → proportionally fewer people per bus
                r.base = clamp(r.base * interval / r.interval, 8, 100);
                log.info("Sim {}: interval {} → {} min, base occupancy now {}%", route, r.interval, interval, Math.round(r.base));
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
        for (int i = 0; i < n; i++) {
            var b = borts.get(i);
            buses.add(new SimBus((String) b.get("bort"), (String) b.get("type"), ((Number) b.get("capacity")).intValue(),
                    r.line.length() * i / n, clamp(r.base + rnd.nextDouble() * 12 - 6, 8, 100)));
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
