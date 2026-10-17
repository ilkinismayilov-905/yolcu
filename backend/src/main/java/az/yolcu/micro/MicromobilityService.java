package az.yolcu.micro;

import az.yolcu.common.ApiException;
import az.yolcu.common.GeoUtil;
import az.yolcu.geo.GeoStore;
import az.yolcu.geo.StopService;
import az.yolcu.micro.dto.BikeParking;
import az.yolcu.micro.dto.Micromobility;
import az.yolcu.micro.dto.Scooter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** BE-3.5: bike parkings are the real AYNA layer; scooters are SIM (0–5 within 150 m of each seeded stop, ±1 per minute). */
@Service
public class MicromobilityService {
    static final int MAX_PER_STOP = 5, SCATTER_M = 150, MAX_RADIUS = 5_000;
    // shortcut: bike_parkings.kmz has no capacity attribute, every parking reports 10 places; replace when data has it
    static final int BIKE_PARKING_CAPACITY = 10;

    private final List<BikeParking> parkings = new ArrayList<>();
    private final List<double[]> stopPoints = new ArrayList<>();
    private final List<List<Scooter>> scooters = new ArrayList<>(); // per stop
    private final Random rnd = new Random(12);
    private int nextId = 1;

    public MicromobilityService(GeoStore geo, StopService stops) {
        geo.find("bike-parkings").ifPresent(fc -> {
            int i = 0;
            for (var f : fc.path("features")) {
                i++;
                parkings.add(new BikeParking("bp-" + i, f.at("/geometry/coordinates/1").asDouble(),
                        f.at("/geometry/coordinates/0").asDouble(), BIKE_PARKING_CAPACITY));
            }
        });
        for (var s : stops.all()) {
            stopPoints.add(new double[]{s.lat(), s.lon()});
            var list = new ArrayList<Scooter>();
            int n = rnd.nextInt(MAX_PER_STOP + 1);
            for (int k = 0; k < n; k++) list.add(spawn(s.lat(), s.lon()));
            scooters.add(list);
        }
    }

    @Scheduled(fixedRate = 60_000, initialDelay = 60_000)
    public synchronized void tick() {
        for (int i = 0; i < scooters.size(); i++) {
            var list = scooters.get(i);
            int delta = rnd.nextInt(3) - 1;
            if (delta > 0 && list.size() < MAX_PER_STOP) list.add(spawn(stopPoints.get(i)[0], stopPoints.get(i)[1]));
            if (delta < 0 && !list.isEmpty()) list.remove(rnd.nextInt(list.size()));
        }
    }

    public synchronized Micromobility near(double lat, double lon, double radius) {
        if (radius <= 0 || radius > MAX_RADIUS) throw ApiException.validation("radius: 1–" + MAX_RADIUS + " m olmalıdır");
        var bp = parkings.stream()
                .filter(p -> GeoUtil.distanceMeters(lat, lon, p.lat(), p.lon()) <= radius)
                .sorted(Comparator.comparingDouble(p -> GeoUtil.distanceMeters(lat, lon, p.lat(), p.lon())))
                .toList();
        var sc = scooters.stream().flatMap(List::stream)
                .filter(s -> GeoUtil.distanceMeters(lat, lon, s.lat(), s.lon()) <= radius)
                .sorted(Comparator.comparingDouble(s -> GeoUtil.distanceMeters(lat, lon, s.lat(), s.lon())))
                .toList();
        return new Micromobility(bp, sc, "/api/v1/geo/layers/bike-lanes");
    }

    private Scooter spawn(double lat, double lon) {
        double r = SCATTER_M * Math.sqrt(rnd.nextDouble()), a = rnd.nextDouble() * 2 * Math.PI;
        double dLat = r * Math.cos(a) / 110_540, dLon = r * Math.sin(a) / (111_320 * Math.cos(Math.toRadians(lat)));
        return new Scooter("sc-" + nextId++, Math.round((lat + dLat) * 1e5) / 1e5, Math.round((lon + dLon) * 1e5) / 1e5,
                30 + rnd.nextInt(66));
    }
}
