package az.yolcu.traffic;

import az.yolcu.common.AppClock;
import az.yolcu.common.GeoUtil;
import az.yolcu.config.YolcuProperties;
import az.yolcu.geo.GeoStore;
import az.yolcu.geo.Polyline;
import az.yolcu.geo.StopService;
import az.yolcu.sim.HubSimulator;
import az.yolcu.stream.StreamEvent;
import az.yolcu.traffic.dto.TrafficSegment;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * BE-3.4: congestion segments (mocks/traffic_segments.json). Geometry is cut from the real bus trajectory that passes
 * all anchor stops in order with the shortest path; nothing is drawn as a straight line between stops.
 * Level follows a time-of-day profile: baseLevel in peaks, one step lighter otherwise.
 */
@Service
public class TrafficService {
    static final double MAX_ANCHOR_M = 300, MAX_DETOUR = 1.6;
    private static final Logger log = LoggerFactory.getLogger(TrafficService.class);
    private static final List<String> LEVELS = List.of("FREE", "SLOW", "JAM");
    private static final Map<String, Integer> DEFAULT_SPEED = Map.of("FREE", 30, "SLOW", 18, "JAM", 10);

    /** Result of matching a segment to a trajectory; line points are {lat, lon}. */
    public record Match(String route, double maxAnchorM, double lengthM, List<double[]> line) {}

    record Segment(String id, String name, String baseLevel, int baseSpeed, Match match) {}

    private final List<Segment> segments = new ArrayList<>();
    private final AppClock clock;
    private final ApplicationEventPublisher events;
    private volatile List<TrafficSegment> snapshot = List.of();

    public TrafficService(YolcuProperties props, ObjectMapper mapper, GeoStore geo, StopService stops, HubSimulator hubs,
                          AppClock clock, ApplicationEventPublisher events) throws IOException {
        this.clock = clock;
        this.events = events;
        var lines = new ArrayList<Map.Entry<String, Polyline>>();
        geo.routeLines().forEach((route, parts) -> parts.forEach(p -> lines.add(Map.entry(route, p))));
        for (var s : mapper.readTree(props.mocksPath().resolve("traffic_segments.json").toFile())) {
            var id = s.path("id").asText();
            // anchors: per stop the real hub point (metro centroid) and/or the seeded bus stop — whichever the line passes
            var anchors = new ArrayList<List<double[]>>();
            for (var stop : s.path("stops")) {
                var alts = new ArrayList<double[]>();
                hubs.coords(stop.asText()).ifPresent(alts::add);
                stops.byId(stop.asText()).ifPresent(st -> alts.add(new double[]{st.lat(), st.lon()}));
                if (!alts.isEmpty()) anchors.add(alts);
            }
            var m = anchors.size() == s.path("stops").size() && anchors.size() >= 2 ? match(anchors, lines) : null;
            if (m == null) {
                log.warn("Traffic {}: no trajectory passes all anchors {} within {} m (detour ≤ {}×) — segment not served",
                        id, s.path("stops"), (int) MAX_ANCHOR_M, MAX_DETOUR);
                continue;
            }
            log.info("Traffic {}: route {}, max anchor distance {} m{}, length {} m, {} pts", id, m.route(),
                    Math.round(m.maxAnchorM()), m.maxAnchorM() > 200 ? " (loose: seeded stop coordinates are approximate)" : "",
                    Math.round(m.lengthM()), m.line().size());
            segments.add(new Segment(id, s.path("name").asText(), s.path("baseLevel").asText(), s.path("speedKmh").asInt(), m));
        }
        snapshot = compute();
    }

    @Scheduled(fixedRate = 10_000, initialDelay = 10_000)
    public void tick() {
        var next = compute();
        boolean changed = next.size() != snapshot.size();
        for (int i = 0; !changed && i < next.size(); i++) changed = !next.get(i).level().equals(snapshot.get(i).level());
        snapshot = next;
        if (changed) events.publishEvent(StreamEvent.all("traffic.updated", next));
    }

    public List<TrafficSegment> segments() { return snapshot; }

    /** segment id → matched trajectory. */
    Map<String, Match> matches() {
        var m = new LinkedHashMap<String, Match>();
        segments.forEach(s -> m.put(s.id(), s.match()));
        return m;
    }

    private List<TrafficSegment> compute() {
        double t = clock.hourOfDay();
        boolean peak = (t >= 7 && t < 10) || (t >= 17 && t < 20);
        return segments.stream().map(s -> {
            var level = peak ? s.baseLevel() : LEVELS.get(Math.max(0, LEVELS.indexOf(s.baseLevel()) - 1));
            int speed = level.equals(s.baseLevel()) ? s.baseSpeed() : DEFAULT_SPEED.get(level);
            var line = s.match().line().stream().map(p -> List.of(round5(p[0]), round5(p[1]))).toList();
            return new TrafficSegment(s.id(), s.name(), level, speed, line);
        }).toList();
    }

    /**
     * Shortest in-order cut over all trajectories (both directions): every anchor must have a pass within MAX_ANCHOR_M,
     * passes must come in anchor order along the line (wrap only on closed loops), and the cut must not be longer than
     * MAX_DETOUR × the anchor-to-anchor distance.
     */
    static Match match(List<List<double[]>> anchors, List<Map.Entry<String, Polyline>> lines) {
        double direct = 0;
        for (int k = 0; k + 1 < anchors.size(); k++) {
            var a = anchors.get(k).get(0);
            var b = anchors.get(k + 1).get(0);
            direct += GeoUtil.distanceMeters(a[0], a[1], b[0], b[1]);
        }
        double cap = Math.max(300, direct) * MAX_DETOUR;
        Match best = null;
        for (var e : lines) {
            for (var line : List.of(e.getValue(), e.getValue().reversed())) {
                var passes = new ArrayList<List<double[]>>();
                for (var alts : anchors) {
                    var ps = new ArrayList<double[]>();
                    for (var a : alts) ps.addAll(line.passes(a[0], a[1], MAX_ANCHOR_M));
                    if (ps.isEmpty()) break;
                    passes.add(ps);
                }
                if (passes.size() < anchors.size()) continue;
                var pick = new int[passes.size()];
                while (true) {
                    double len = 0, maxD = 0;
                    boolean ok = true;
                    for (int k = 0; k < pick.length; k++) {
                        var p = passes.get(k).get(pick[k]);
                        maxD = Math.max(maxD, p[0]);
                        if (k == 0) continue;
                        double d = p[1] - passes.get(k - 1).get(pick[k - 1])[1];
                        if (d < 0) {
                            if (!line.closed()) { ok = false; break; }
                            d += line.length();
                        }
                        len += d;
                    }
                    if (ok && len <= cap && (best == null || len < best.lengthM())) {
                        double s0 = passes.get(0).get(pick[0])[1], s1 = passes.get(pick.length - 1).get(pick[pick.length - 1])[1];
                        best = new Match(e.getKey(), maxD, len, line.slice(s0, s1));
                    }
                    int k = pick.length - 1; // next combination (odometer)
                    while (k >= 0 && ++pick[k] == passes.get(k).size()) pick[k--] = 0;
                    if (k < 0) break;
                }
            }
        }
        return best;
    }

    private static double round5(double v) { return Math.round(v * 1e5) / 1e5; }
}
