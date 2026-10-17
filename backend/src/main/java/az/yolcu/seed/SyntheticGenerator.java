package az.yolcu.seed;

import az.yolcu.geo.StopService;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** BE-1.5: 5 000 synthetic trips (OD weights from the web mockup) + 300 reviews. Fixed seed → same data every run. */
@Component
public class SyntheticGenerator {
    static final int TRIPS = 5000;
    static final int REVIEWS = 300;
    // zones = OD matrix order in design/web_dashboard.html; value = stop id in mocks/stops_seed.json
    static final String[] ZONE_NAMES = {"Avtovağzal", "Koroğlu", "28 May", "Nizami", "Gənclik", "Nərimanov"};
    static final String[] ZONE_STOPS = {"avtovagzal", "koroglu", "may28", "nizami", "genclik", "nerimanov"};
    static final double[][] OD = {
            {0, 8.2, 14.6, 6.1, 5.3, 4.4}, {3.1, 0, 9.8, 4.2, 7.7, 12.9}, {5.2, 6.4, 0, 7.9, 8.8, 6.0},
            {2.4, 3.3, 8.1, 0, 4.6, 3.9}, {2.0, 6.9, 7.4, 3.8, 0, 5.7}, {1.8, 10.4, 6.6, 3.2, 4.9, 0}};
    static final Map<String, Double> OPERATOR_RATING = Map.of("bb", 4.6, "xf", 3.9, "ct", 4.2, "6d", 3.5);
    private static final String[] CROWD = {"EMPTY", "NORMAL", "FULL", "OVERFULL"};
    private static final String[] GOOD_TAGS = {"Nəzakətli sürücü", "Təmiz salon", "Vaxtında gəldi"};
    private static final String[] BAD_TAGS = {"Kəskin sürüş", "Kondisioner işləmir", "Çox dolu idi"};

    private final JdbcTemplate jdbc;
    private final StopService stops;

    public SyntheticGenerator(JdbcTemplate jdbc, StopService stops) {
        this.jdbc = jdbc;
        this.stops = stops;
    }

    public void generate(OffsetDateTime now, List<Long> passengers) {
        var rnd = new Random(2026);
        var buses = jdbc.queryForList("select b.bort, b.route, b.operator_id, o.code from buses b join operators o on o.id = b.operator_id order by b.id");
        double total = 0;
        for (var row : OD) for (var w : row) total += w;
        var today = now.truncatedTo(ChronoUnit.DAYS);

        var trips = new ArrayList<Object[]>(TRIPS);
        for (int i = 0; i < TRIPS; i++) {
            // OD cell by weight
            double x = rnd.nextDouble() * total;
            int from = 0, to = 0;
            outer:
            for (from = 0; from < 6; from++) for (to = 0; to < 6; to++) if ((x -= OD[from][to]) < 0) break outer;
            if (from == 6) { from = 5; to = 4; } // float rounding at the very end
            // 70% in the 07:00–10:00 peak, the rest spread over 06:00–23:00
            int minute = rnd.nextDouble() < 0.7 ? 7 * 60 + rnd.nextInt(180) : 6 * 60 + rnd.nextInt(17 * 60);
            var start = today.minusDays(1 + rnd.nextInt(30)).plusMinutes(minute);
            var bus = buses.get(rnd.nextInt(buses.size()));
            var alight = stops.byId(ZONE_STOPS[to]).orElseThrow();
            double p = rnd.nextDouble();
            trips.add(new Object[]{passengers.get(rnd.nextInt(passengers.size())), bus.get("route"), bus.get("bort"),
                    ZONE_STOPS[from], ZONE_STOPS[to], ZONE_NAMES[to],
                    alight.lat() + (rnd.nextDouble() - 0.5) * 0.002, alight.lon() + (rnd.nextDouble() - 0.5) * 0.002,
                    rnd.nextDouble() < 0.85 ? "AUTO" : "MANUAL", p < 0.8 ? "BAKIKART" : p < 0.98 ? "CARD" : "COIN",
                    start, start.plusMinutes(12 + rnd.nextInt(34))});
        }
        jdbc.batchUpdate("insert into trips (user_id, route, bort, from_stop, to_place, alight_stop, alight_lat, alight_lon, "
                + "alight_detected, paid_with, started_at, ended_at, synthetic) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true)", trips);

        var operatorByBort = new HashMap<Object, Map<String, Object>>();
        buses.forEach(b -> operatorByBort.put(b.get("bort"), b));
        var synthetic = jdbc.queryForList("select id, user_id, route, bort, ended_at from trips where synthetic = true order by id");
        var reviews = new ArrayList<Object[]>(REVIEWS);
        int step = synthetic.size() / REVIEWS;
        for (int i = 0; i < REVIEWS; i++) {
            var t = synthetic.get(i * step);
            var bus = operatorByBort.get(t.get("bort"));
            double mean = OPERATOR_RATING.get((String) bus.get("code"));
            int driver = rating(mean, rnd);
            var tags = driver >= 4 ? GOOD_TAGS[rnd.nextInt(GOOD_TAGS.length)] : BAD_TAGS[rnd.nextInt(BAD_TAGS.length)];
            reviews.add(new Object[]{t.get("id"), t.get("user_id"), t.get("route"), t.get("bort"), bus.get("operator_id"),
                    driver, rating(mean, rnd), rating(mean, rnd), CROWD[rnd.nextInt(CROWD.length)], tags, t.get("ended_at")});
        }
        jdbc.batchUpdate("insert into reviews (trip_id, user_id, route, bort, operator_id, driver_rating, road_rating, "
                + "bus_rating, crowd_level, tags, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", reviews);
    }

    /** Two-point distribution around the mean (4.6 → 60% fives, 40% fours) so operator averages land on target. */
    static int rating(double mean, Random rnd) {
        int base = (int) Math.floor(mean);
        return Math.min(5, base + (rnd.nextDouble() < mean - base ? 1 : 0));
    }
}
