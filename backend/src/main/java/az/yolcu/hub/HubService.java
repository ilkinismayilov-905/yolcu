package az.yolcu.hub;

import az.yolcu.biletim.BiletimService;
import az.yolcu.common.ApiException;
import az.yolcu.feed.FeedService;
import az.yolcu.geo.StopService;
import az.yolcu.hub.dto.AdminHub;
import az.yolcu.occupancy.OccupancyService;
import az.yolcu.occupancy.dto.RouteOccupancy;
import az.yolcu.sim.HubSimulator;
import az.yolcu.sim.dto.LiveHub;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** BE-8.4: hub cameras (SIM counts) + level, wait and a rule-based recommendation (03_API §9 "Hub kameraları"). */
@Service
public class HubService {
    static final double HIGH = 0.8, MEDIUM = 0.55;   // count / capacity
    static final int MAX_WAIT_MIN = 20;
    static final Set<String> HUBS = Set.of("may28", "koroglu", "avtovagzal"); // design #p-hubs "Hub-lar"; the rest are "Dayanacaqlar"
    static final String TERMINAL = "avtovagzal";
    static final int EXPRESS_TICKETS = 40, EXPRESS_WITHIN_MIN = 30;

    /** route/iv set only for an interval recommendation. */
    record Rec(String text, String route, int iv) {}

    private final HubSimulator sim;
    private final StopService stops;
    private final OccupancyService occupancy;
    private final BiletimService biletim;
    private final FeedService feed;

    public HubService(HubSimulator sim, StopService stops, OccupancyService occupancy, BiletimService biletim, FeedService feed) {
        this.sim = sim;
        this.stops = stops;
        this.occupancy = occupancy;
        this.biletim = biletim;
        this.feed = feed;
    }

    public List<AdminHub> hubs() {
        var routes = occupancy.routes().stream().collect(Collectors.toMap(RouteOccupancy::route, r -> r));
        return sim.hubs().stream().map(h -> {
            var level = level(h);
            var rec = rec(h, level, routes);
            return new AdminHub(h.id(), h.name(), h.lat(), h.lon(), h.count(), h.capacity(), h.trend10(), h.forecast15(), level,
                    (int) Math.min(MAX_WAIT_MIN, Math.round(2 + 10.0 * h.count() / h.capacity())),
                    sim.camera(h.id()).orElse(""), rec == null ? null : rec.text(), HUBS.contains(h.id()) ? "HUB" : "STOP");
        }).toList();
    }

    /** AI alert with the recommendation; an interval recommendation is also applied (route.updated + its own AI alert). */
    public void apply(String id) {
        var h = sim.hubs().stream().filter(x -> x.id().equals(id)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Hub tapılmadı: " + id));
        var rec = rec(h, level(h), occupancy.routes().stream().collect(Collectors.toMap(RouteOccupancy::route, r -> r)));
        if (rec == null) return; // nothing to apply
        if (rec.route() != null) occupancy.applyInterval(rec.route(), rec.iv());
        feed.add("AI", h.name() + ": tövsiyə tətbiq olundu", rec.text(), "p-hubs");
    }

    static String level(LiveHub h) {
        double r = (double) h.count() / h.capacity();
        return r >= HIGH ? "HIGH" : r >= MEDIUM ? "MEDIUM" : "LOW";
    }

    /** LOW → null. Terminal: big Biletim arrival soon → express. Else the fullest serving route the engine wants tighter. */
    private Rec rec(LiveHub h, String level, Map<String, RouteOccupancy> routes) {
        if (level.equals("LOW")) return null;
        if (h.id().equals(TERMINAL)) {
            var big = biletim.soon(EXPRESS_WITHIN_MIN, EXPRESS_TICKETS);
            if (big.isPresent()) return new Rec(big.get().time() + " " + big.get().from() + " reysi üçün M1 ekspressi işə sal", null, 0);
        }
        var serving = stops.byId(h.id()).map(s -> s.routes()).orElse(List.of());
        var best = serving.stream().map(routes::get).filter(r -> r != null && r.recommendedMin() < r.intervalMin())
                .max(Comparator.comparingInt(RouteOccupancy::avgOccupancy));
        if (best.isPresent()) {
            var r = best.get();
            return new Rec(r.route() + " intervalı " + r.intervalMin() + " → " + r.recommendedMin() + " dəq", r.route(), r.recommendedMin());
        }
        return level.equals("HIGH") ? new Rec("Platformaya nəzarətçi göndər, gözləyənlərə tətbiqdə metro təklif et", null, 0) : null;
    }
}
