package az.yolcu.sim;

import az.yolcu.common.AppClock;
import az.yolcu.config.YolcuProperties;
import az.yolcu.geo.GeoStore;
import az.yolcu.geo.StopService;
import az.yolcu.sim.dto.HubCount;
import az.yolcu.sim.dto.LiveHub;
import az.yolcu.stream.StreamEvent;
import az.yolcu.user.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** BE-3.3: people waiting at hubs (camera SIM). Formula from docs/04_DATA §5; coordinates are real metro stations. */
@Service
public class HubSimulator {
    private static final Logger log = LoggerFactory.getLogger(HubSimulator.class);
    /** hub id → station name in data/processed/metro_ridership.geojson (centroid of the real exits). */
    static final Map<String, String> STATION = Map.of("koroglu", "Koroğlu", "may28", "28 May", "avtovagzal", "Avtovağzal",
            "genclik", "Gənclik", "nizami", "Nizami", "elmler", "Elmlər Akademiyası", "nerimanov", "Nəriman Nərimanov",
            "sahil", "Sahil");

    record Hub(String id, String name, double lat, double lon, int capacity, int base, String camera) {}

    private final List<Hub> hubs = new ArrayList<>();
    private final double[] noise;
    private final Map<String, Double> boost = new java.util.concurrent.ConcurrentHashMap<>(); // demo: hub id → count factor
    private final Random rnd = new Random(5);
    private final AppClock clock;
    private final ApplicationEventPublisher events;
    private volatile List<LiveHub> snapshot = List.of();

    public HubSimulator(YolcuProperties props, ObjectMapper mapper, GeoStore geo, StopService stops, AppClock clock,
                        ApplicationEventPublisher events) throws IOException {
        this.clock = clock;
        this.events = events;
        var stations = new HashMap<String, double[]>();
        geo.find("metro-ridership").ifPresent(fc -> fc.path("features").forEach(f -> stations.put(
                f.path("properties").path("station").asText(),
                new double[]{f.at("/geometry/coordinates/1").asDouble(), f.at("/geometry/coordinates/0").asDouble()})));
        for (var h : mapper.readTree(props.mocksPath().resolve("hubs.json").toFile())) {
            var id = h.path("id").asText();
            var c = stations.get(STATION.get(id));
            if (c == null) { // only when the metro layer is missing (test fixtures)
                var s = stops.byId(id).orElse(null);
                if (s == null) {
                    log.warn("Hub {}: no coordinates (metro station / stop), skipped", id);
                    continue;
                }
                c = new double[]{s.lat(), s.lon()};
                log.warn("Hub {}: metro station not found, using approximate stops_seed point", id);
            }
            hubs.add(new Hub(id, h.path("name").asText(), c[0], c[1], h.path("capacity").asInt(), h.path("base").asInt(),
                    h.path("camera").asText()));
        }
        noise = new double[hubs.size()];
        log.info("Hubs: {} from metro_ridership station centroids", hubs.stream().map(Hub::id).toList());
        compute();
    }

    @Scheduled(fixedRate = 5_000, initialDelay = 5_000)
    public void tick() {
        compute();
        var counts = snapshot.stream().map(h -> new HubCount(h.id(), h.count(), h.trend10(), h.forecast15())).toList();
        events.publishEvent(StreamEvent.toRole(Role.DISPATCHER, "hub.counts", counts));
        events.publishEvent(StreamEvent.toRole(Role.ADMIN, "hub.counts", counts));
    }

    public List<LiveHub> hubs() { return snapshot; }

    public Optional<double[]> coords(String hubId) {
        return hubs.stream().filter(h -> h.id().equals(hubId)).findFirst().map(h -> new double[]{h.lat(), h.lon()});
    }

    /** Camera id from mocks/hubs.json, e.g. "K-201"; empty if the hub is unknown. */
    public Optional<String> camera(String hubId) {
        return hubs.stream().filter(h -> h.id().equals(hubId)).findFirst().map(Hub::camera);
    }

    /** Demo crowd: the hub's count is multiplied by factor until reset; counts are recomputed now. False if unknown. */
    public boolean boost(String hubId, double factor) {
        if (coords(hubId).isEmpty()) return false;
        boost.put(hubId, factor);
        compute();
        return true;
    }

    /** Demo reset: no boosts, noise back to zero. */
    public synchronized void reset() {
        boost.clear();
        java.util.Arrays.fill(noise, 0);
        compute();
    }

    public int totalWaiting() { return snapshot.stream().mapToInt(LiveHub::count).sum(); }

    private synchronized void compute() {
        double t = clock.hourOfDay();
        var out = new ArrayList<LiveHub>();
        for (int i = 0; i < hubs.size(); i++) {
            var h = hubs.get(i);
            // noise: bounded random walk within ±5 % so the count moves smoothly instead of jumping
            noise[i] = Math.max(-0.05, Math.min(0.05, noise[i] + (rnd.nextDouble() - 0.5) * 0.03));
            double k = boost.getOrDefault(h.id(), 1.0);
            double now = curve(h.base(), t) * k;
            int count = (int) Math.round(now * (1 + noise[i]));
            int trend = (int) Math.round(now - curve(h.base(), t - 10 / 60.0) * k); // last 10 min, formula without noise
            int forecast = (int) Math.round(count + trend * 1.5);
            out.add(new LiveHub(h.id(), h.name(), h.lat(), h.lon(), count, h.capacity(), trend, forecast));
        }
        snapshot = List.copyOf(out);
    }

    static double curve(int base, double t) {
        return base * (1 + 0.35 * Math.exp(-Math.pow(t - 8.4, 2) / 1.4) + 0.25 * Math.exp(-Math.pow(t - 18.2, 2) / 2));
    }
}
