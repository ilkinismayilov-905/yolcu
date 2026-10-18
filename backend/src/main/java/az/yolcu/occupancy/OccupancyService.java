package az.yolcu.occupancy;

import az.yolcu.common.ApiException;
import az.yolcu.feed.FeedService;
import az.yolcu.geo.StopService;
import az.yolcu.occupancy.dto.Bunching;
import az.yolcu.occupancy.dto.OccupancyBus;
import az.yolcu.occupancy.dto.RouteOccupancy;
import az.yolcu.occupancy.dto.SimulateResult;
import az.yolcu.sim.SimulationService;
import az.yolcu.sim.dto.BusPosition;
import az.yolcu.stream.StreamEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** BE-8.2/8.3: route occupancy from the live sim + IntervalEngine, interval apply, auto mode, bunching detector. */
@Service
public class OccupancyService {
    static final double BUNCH_MIN = 3;           // 02_ARCHITECTURE §6: consecutive buses < 3 min apart
    static final long BUNCH_ALERT_COOLDOWN_MS = 5 * 60_000;
    static final long SETTLE_MS = 60_000;        // sim occupancy needs ~30 s to follow a new interval
    static final double HOLD_MIN = 3;            // design #p-occ: "3 dəqiqə saxlanılsa"
    private static final Logger log = LoggerFactory.getLogger(OccupancyService.class);

    private final SimulationService sim;
    private final StopService stops;
    private final FeedService feed;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final Map<String, Long> changedAt = new ConcurrentHashMap<>();
    private final Map<String, Long> bunchAlertAt = new ConcurrentHashMap<>();
    private final Set<String> bunched = ConcurrentHashMap.newKeySet();
    private volatile boolean primed; // bunching present at boot is already in the seeded feed (and survives devtools restarts)

    public OccupancyService(SimulationService sim, StopService stops, FeedService feed, JdbcTemplate jdbc,
                            ApplicationEventPublisher events) {
        this.sim = sim;
        this.stops = stops;
        this.feed = feed;
        this.jdbc = jdbc;
        this.events = events;
    }

    /** Simulated routes (with live buses), fullest first. */
    public List<RouteOccupancy> routes() {
        var operators = new HashMap<String, String>();
        jdbc.query("select code, name from operators", rs -> { operators.put(rs.getString(1), rs.getString(2)); });
        var out = new ArrayList<RouteOccupancy>();
        for (var c : jdbc.queryForList("select route, interval_min, bus_count, auto_mode from route_config")) {
            var row = row((String) c.get("route"), ((Number) c.get("interval_min")).intValue(),
                    ((Number) c.get("bus_count")).intValue(), (Boolean) c.get("auto_mode"), operators);
            if (row != null) out.add(row);
        }
        out.sort(Comparator.comparingInt(RouteOccupancy::avgOccupancy).reversed());
        return out;
    }

    public RouteOccupancy route(String route) {
        return routes().stream().filter(r -> r.route().equals(route)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Marşrut tapılmadı: " + route));
    }

    public List<OccupancyBus> buses(String route) {
        route(route);
        return sim.buses(route).stream()
                .map(b -> new OccupancyBus(b.bort(), b.type(), near(b), people(b), b.capacity())).toList();
    }

    public SimulateResult simulate(String route, int iv) {
        var all = routes();
        var r = all.stream().filter(x -> x.route().equals(route)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Marşrut tapılmadı: " + route));
        var res = IntervalEngine.simulate(r.avgOccupancy(), r.intervalMin(), r.busCount(), iv);
        int extra = res.busesNeeded() - r.busCount();
        // donor = emptiest other route (design #p-occ note: "Təklif: 33-dən 2")
        var donor = all.stream().filter(x -> !x.route().equals(route)).reduce((a, b) -> b).map(RouteOccupancy::route).orElse("depo");
        String note = extra > 0 ? extra + " əlavə avtobus lazımdır. Təklif: " + donor + "-dən " + Math.min(2, extra)
                + (extra > 2 ? ", depodan " + (extra - 2) : "") + "."
                : extra < 0 ? (-extra) + " avtobus azad olur, tələbatı yüksək marşruta verilə bilər." : "Avtobus sayı dəyişmir.";
        return new SimulateResult(res.occupancy(), res.waitMin(), res.busesNeeded(), res.satAfter(), note);
    }

    /** Dispatcher applies an interval: route_config (the sim follows it), route.updated SSE, AI alert. */
    public RouteOccupancy applyInterval(String route, int iv) { return apply(route(route), iv, false); }

    public void setAuto(boolean enabled) {
        jdbc.update("update route_config set auto_mode = ?", enabled);
        log.info("Occupancy auto mode {}", enabled ? "on" : "off");
    }

    /** Auto mode: apply the recommendation on every auto route, except right after a change (occupancy still settling). */
    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public void autoTick() {
        long now = System.currentTimeMillis();
        for (var r : routes()) {
            boolean settling = now - changedAt.getOrDefault(r.route(), 0L) < SETTLE_MS;
            if (r.autoMode() && !settling && r.recommendedMin() != r.intervalMin()) apply(r, r.recommendedMin(), true);
        }
    }

    /** Tightest pair per route; front = the fuller bus of the pair (UI: "öndəki doludur, arxadakı yarıboşdur"). */
    public List<Bunching> bunching() {
        return tightest().stream().map(g -> {
            boolean swap = g.back().occupancy() > g.front().occupancy();
            var full = swap ? g.back() : g.front();
            var empty = swap ? g.front() : g.back();
            return new Bunching(g.route(), ref(full), ref(empty), (int) Math.max(1, Math.floor(g.gapMin())), near(full));
        }).toList();
    }

    private List<SimulationService.Gap> tightest() {
        var best = new HashMap<String, SimulationService.Gap>();
        for (var g : sim.gaps(BUNCH_MIN)) best.merge(g.route(), g, (a, b) -> a.gapMin() <= b.gapMin() ? a : b);
        return best.values().stream().sorted(Comparator.comparingDouble(SimulationService.Gap::gapMin)).toList();
    }

    /** OCC alert when a route starts bunching (not on the first tick), at most once per route per cooldown. */
    @Scheduled(fixedRate = 10_000, initialDelay = 5_000)
    public synchronized void bunchingTick() {
        var now = System.currentTimeMillis();
        var current = new HashSet<String>();
        for (var b : bunching()) {
            current.add(b.route());
            if (!primed || bunched.contains(b.route()) || now - bunchAlertAt.getOrDefault(b.route(), 0L) < BUNCH_ALERT_COOLDOWN_MS) continue;
            bunchAlertAt.put(b.route(), now);
            feed.add("OCC", b.route() + ": iki avtobus topa yığılıb",
                    b.front().bort() + " və " + b.back().bort() + " arası " + b.gapMin() + " dəq", "p-occ");
        }
        bunched.retainAll(current);
        bunched.addAll(current);
        primed = true;
    }

    /** Tells the physically trailing bus of the tightest pair to wait HOLD_MIN minutes, so the gap opens up. */
    public Map<String, Object> message(String route) {
        var back = tightest().stream().filter(x -> x.route().equals(route)).findFirst()
                .orElseThrow(() -> ApiException.notFound(route + " marşrutunda topa yığılma yoxdur")).back();
        sim.hold(route, back.bort(), HOLD_MIN);
        var text = back.bort() + " sürücüsünə: " + near(back) + " dayanacağında " + (int) HOLD_MIN + " dəqiqə gözləyin";
        log.info("Bunching {}: message sent: {}", route, text);
        return Map.of("ok", true, "text", text);
    }

    /** Tests: forget settling and bunching-alert state. */
    void resetState() {
        changedAt.clear();
        bunchAlertAt.clear();
        bunched.clear();
        primed = false;
    }

    private RouteOccupancy apply(RouteOccupancy r, int iv, boolean auto) {
        if (iv == r.intervalMin()) return r;
        jdbc.update("update route_config set interval_min = ?, updated_at = current_timestamp where route = ?", iv, r.route());
        changedAt.put(r.route(), System.currentTimeMillis());
        var res = IntervalEngine.simulate(r.avgOccupancy(), r.intervalMin(), r.busCount(), iv);
        var updated = route(r.route());
        events.publishEvent(StreamEvent.all("route.updated", updated));
        var title = r.route() + ": interval " + r.intervalMin() + " → " + iv + " dəq" + (auto ? " (avtomatik rejim)" : " tətbiq olundu");
        var body = "Gözlənilən doluluq " + r.avgOccupancy() + "% → " + res.occupancy() + "%, məmnunluq " + dec(res.satNow())
                + " → " + dec(res.satAfter()) + ". Lazım olan avtobus: " + res.busesNeeded();
        feed.add("AI", title, body, "p-occ");
        log.info("Occupancy {}: interval {} → {} ({})", r.route(), r.intervalMin(), iv, auto ? "auto" : "dispatcher");
        return updated;
    }

    private RouteOccupancy row(String route, int iv, int busCount, boolean auto, Map<String, String> operators) {
        var buses = sim.buses(route);
        if (buses.isEmpty()) return null; // not simulated (e.g. H1 has no borts on LINE): no live occupancy
        int occ = (int) Math.round(buses.stream().mapToInt(BusPosition::occupancy).average().orElseThrow());
        var p = sim.routeParams(route);
        var res = IntervalEngine.simulate(occ, iv, busCount);
        var type = p.path("type").asText(buses.get(0).type());
        return new RouteOccupancy(route, type, operators.getOrDefault(p.path("operator").asText(), ""), busCount, occ, iv,
                res.intervalMin(), res.satNow(), res.satAfter(), auto);
    }

    private String near(BusPosition b) {
        return stops.near(b.lat(), b.lon(), Double.MAX_VALUE).stream().findFirst().map(s -> s.name()).orElse("");
    }

    private static int people(BusPosition b) { return (int) Math.round(b.occupancy() * b.capacity() / 100.0); }

    private static Bunching.BusRef ref(BusPosition b) { return new Bunching.BusRef(b.bort(), people(b)); }

    private static String dec(double v) { return String.valueOf(v).replace('.', ','); }
}
