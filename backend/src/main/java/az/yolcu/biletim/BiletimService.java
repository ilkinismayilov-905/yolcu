package az.yolcu.biletim;

import az.yolcu.biletim.BiletimAdapter.Arrival;
import az.yolcu.biletim.dto.BiletimArrival;
import az.yolcu.common.AppClock;
import az.yolcu.geo.StopService;
import az.yolcu.occupancy.OccupancyService;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** BE-8.5: Biletim arrivals + transfer estimate and level; the demo Gəncə arrival is pinned first until reset. */
@Service
public class BiletimService {
    static final int HIGH_TICKETS = 35, MEDIUM_TICKETS = 20;
    // shortcut: free places per city bus at Avtovağzal is a constant, use live occupancy × capacity if it matters
    static final int FREE_PER_BUS = 15;
    static final String TERMINAL = "avtovagzal";
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final BiletimAdapter adapter;
    private final OccupancyService occupancy;
    private final StopService stops;
    private final AppClock clock;
    private volatile Arrival demo;

    public BiletimService(BiletimAdapter adapter, OccupancyService occupancy, StopService stops, AppClock clock) {
        this.adapter = adapter;
        this.occupancy = occupancy;
        this.stops = stops;
        this.clock = clock;
    }

    public List<BiletimArrival> arrivals() {
        double headway = headway();
        return upcoming().stream().map(a -> dto(a, headway)).toList();
    }

    /** First upcoming arrival within `minutes` with at least `tickets` sold (hub recommendation rule). */
    public Optional<BiletimArrival> soon(int minutes, int tickets) {
        var limit = clock.now().plusMinutes(minutes);
        return upcoming().stream().filter(a -> a.ticketsSold() >= tickets && !a.at().isAfter(limit)).findFirst()
                .map(a -> dto(a, headway()));
    }

    /** Demo ganja-arrival: Gəncə, 45 tickets, now + 5 min (replaces an earlier demo arrival). */
    public BiletimArrival addDemoArrival() {
        demo = new Arrival(clock.now().truncatedTo(ChronoUnit.MINUTES).plusMinutes(5), "Gəncə", "90-AB-205", 45, "Yoldadır, 6 km");
        return dto(demo, headway());
    }

    public void resetDemo() { demo = null; }

    /** Sorted by time; the demo arrival (if still upcoming) is always first. */
    private List<Arrival> upcoming() {
        var now = clock.now();
        var out = new ArrayList<>(adapter.arrivals().stream().filter(a -> !a.at().isBefore(now))
                .sorted(Comparator.comparing(Arrival::at)).toList());
        var d = demo;
        if (d != null && !d.at().isBefore(now)) out.add(0, d);
        return out;
    }

    private BiletimArrival dto(Arrival a, double headway) {
        int next = (int) Math.max(1, Math.round((double) a.ticketsSold() / FREE_PER_BUS * headway));
        var level = a.ticketsSold() >= HIGH_TICKETS ? "HIGH" : a.ticketsSold() >= MEDIUM_TICKETS ? "MEDIUM" : "LOW";
        return new BiletimArrival(a.at().atZoneSameInstant(AppClock.ZONE).format(HHMM), a.from(), a.plate(), a.ticketsSold(),
                a.status(), next, level);
    }

    /** Combined headway (min) of the simulated city routes serving Avtovağzal: 1 / Σ(1/interval). */
    private double headway() {
        var serving = stops.byId(TERMINAL).map(s -> s.routes()).orElse(List.of());
        double freq = occupancy.routes().stream().filter(r -> serving.contains(r.route()))
                .mapToDouble(r -> 1.0 / r.intervalMin()).sum();
        return freq > 0 ? 1 / freq : 10;
    }
}
