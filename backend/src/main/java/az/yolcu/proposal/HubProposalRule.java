package az.yolcu.proposal;

import az.yolcu.common.GeoUtil;
import az.yolcu.geo.GeoStore;
import az.yolcu.geo.Polyline;
import az.yolcu.geo.StopService;
import az.yolcu.proposal.dto.HubProposal;
import az.yolcu.sim.HubSimulator;
import az.yolcu.traffic.TrafficService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * BE-9.2 (02_ARCHITECTURE §6.2): a segment JAM ≥ 20 min → for each seeded stop within 300 m of it, at the closest
 * point of the real street line: ≥ 3 real bus routes, metro exit ≤ 400 m, ≥ 40 waiting (nearest hub camera), no hub
 * camera within 300 m and no earlier proposal within 300 m → hub_proposals + proposal.created + AI alert.
 */
@Component
public class HubProposalRule {
    static final int MIN_JAM_MIN = 20, STOP_M = 300, MIN_ROUTES = 3, MAX_METRO_M = 400, MIN_WAITING = 40;
    // §6.2 says "existing hub > 500 m", but every hub camera is a metro station, so no point ≤ 400 m from a metro exit
    // near a hub could ever pass; 300 m keeps "not at an existing hub" (Fətəli Xan Xoyski pr. stop: 380 m from the Gənclik camera)
    static final int HUB_CLEAR_M = 300, DUPLICATE_M = 300;
    static final int ROUTE_PASS_M = 50, BIKE_M = 50;
    // shortcut: no hourly counts per station, peak hour ≈ 6 % of metro_ridership avgDaily2026; replace with hourly data
    static final double PEAK_HOUR_SHARE = 0.06;
    private static final Logger log = LoggerFactory.getLogger(HubProposalRule.class);

    record Exit(String station, double lat, double lon) {}

    private final TrafficService traffic;
    private final StopService stops;
    private final HubSimulator hubs;
    private final GeoStore geo;
    private final HubProposalService proposals;
    private final List<Exit> exits = new ArrayList<>();
    private final Map<String, Integer> ridership = new HashMap<>();
    private final List<double[]> bikes = new ArrayList<>();

    public HubProposalRule(TrafficService traffic, StopService stops, HubSimulator hubs, GeoStore geo, HubProposalService proposals) {
        this.traffic = traffic;
        this.stops = stops;
        this.hubs = hubs;
        this.geo = geo;
        this.proposals = proposals;
        geo.find("metro-exits").ifPresent(fc -> fc.path("features").forEach(f -> exits.add(new Exit(
                f.path("properties").path("station").asText(), f.at("/geometry/coordinates/1").asDouble(),
                f.at("/geometry/coordinates/0").asDouble()))));
        geo.find("metro-ridership").ifPresent(fc -> fc.path("features").forEach(f -> ridership.put(
                f.path("properties").path("station").asText(), f.path("properties").path("avgDaily2026").asInt())));
        geo.find("bike-parkings").ifPresent(fc -> fc.path("features").forEach(f -> bikes.add(new double[]{
                f.at("/geometry/coordinates/1").asDouble(), f.at("/geometry/coordinates/0").asDouble()})));
    }

    @Scheduled(fixedRate = 30_000, initialDelay = 30_000)
    public void tick() { check(false); }

    /**
     * Evaluates every JAM segment now; returns the proposals created by this run. again = also propose a place that
     * already has a proposal (demo trigger, so the scenario can be replayed without reset).
     */
    public synchronized List<HubProposal> check(boolean again) {
        var created = new ArrayList<HubProposal>();
        var live = hubs.hubs();
        for (var jam : traffic.jams()) {
            if (jam.minutes() < MIN_JAM_MIN) continue;
            var line = new Polyline(jam.line());
            for (var stop : stops.all()) {
                var near = line.nearest(stop.lat(), stop.lon());
                if (near[0] > STOP_M) continue;
                var p = line.at(near[1]);
                double lat = round5(p[0]), lon = round5(p[1]);
                var hub = live.stream().min(Comparator.comparingDouble(h -> GeoUtil.distanceMeters(lat, lon, h.lat(), h.lon()))).orElse(null);
                var exit = exits.stream().min(Comparator.comparingDouble(e -> GeoUtil.distanceMeters(lat, lon, e.lat(), e.lon()))).orElse(null);
                if (hub == null || exit == null) continue;
                int hubM = (int) Math.round(GeoUtil.distanceMeters(lat, lon, hub.lat(), hub.lon()));
                int metroM = (int) Math.round(GeoUtil.distanceMeters(lat, lon, exit.lat(), exit.lon()));
                int routes = routesPassing(lat, lon);
                int waiting = hub.count();
                if (hubM <= HUB_CLEAR_M || metroM > MAX_METRO_M || routes < MIN_ROUTES || waiting < MIN_WAITING) continue;
                if (!again && proposals.existsNear(lat, lon, DUPLICATE_M)) continue;
                created.add(propose(jam, stop.id(), lat, lon, exit, metroM, routes, waiting));
            }
        }
        return created;
    }

    private HubProposal propose(TrafficService.Jam jam, String stopId, double lat, double lon, Exit exit, int metroM, int routes,
                                int waiting) {
        int confidence = confidence(routes, metroM, waiting);
        int pax = (int) Math.round(ridership.getOrDefault(exit.station(), 0) * PEAK_HOUR_SHARE / 100) * 100;
        var items = new ArrayList<String>();
        items.add("Avtobus cibi, 2 avtobusluq, " + jam.name() + " sağ tərəf");
        boolean bike = bikes.stream().anyMatch(b -> GeoUtil.distanceMeters(lat, lon, b[0], b[1]) <= BIKE_M);
        if (!bike) items.add("Velo parkinq, 20 yer və skuter zonası");
        if (metroM <= MAX_METRO_M) items.add("Metroya qədər işıqlı piyada keçidi");
        items.add("Hub kamerası və canlı gəliş ekranı");
        // shortcut: impact is a rule-of-thumb (walk saved ≈ metroM / 50 m per min, 3/8 of peak-hour flow moves to metro,
        // fixed −40 % waiting and cost band from design/web_dashboard.html); replace with a model after the pilot
        var impact = new HubProposal.Impact(-Math.max(1, Math.round(metroM / 50f)), Math.round(pax * 3 / 8f / 100) * 100, -40,
                "180–240 min");
        log.info("HubProposalRule: {} JAM {} min → stop {} at {},{}: routes {}, metro {} m ({}), waiting {}, confidence {}",
                jam.id(), jam.minutes(), stopId, lat, lon, routes, metroM, exit.station(), waiting, confidence);
        return proposals.create(jam.name() + " – " + exit.station() + " metrosu", lat, lon, jam.minutes(), routes, metroM, waiting,
                pax, confidence, items, impact);
    }

    /** §6.2 formula; capped at 95 so the UI never claims certainty (14 real routes + 300 m metro already give 100). */
    static int confidence(int routes, int metroM, int waiting) {
        return Math.min(95, 50 + Math.min(20, routes * 5) + (metroM < 300 ? 15 : 8) + Math.min(15, waiting / 5));
    }

    /** Distinct real bus routes (routes.geojson) whose trajectory passes within ROUTE_PASS_M of the point. */
    int routesPassing(double lat, double lon) {
        return (int) geo.routeLines().values().stream()
                .filter(parts -> parts.stream().anyMatch(l -> !l.passes(lat, lon, ROUTE_PASS_M).isEmpty())).count();
    }

    private static double round5(double v) { return Math.round(v * 1e5) / 1e5; }
}
