package az.yolcu.feed;

import az.yolcu.common.ApiException;
import az.yolcu.common.AppClock;
import az.yolcu.feed.dto.AlertDto;
import az.yolcu.feed.dto.Kpis;
import az.yolcu.report.ReportCreatedEvent;
import az.yolcu.sim.HubSimulator;
import az.yolcu.sim.SimulationService;
import az.yolcu.sim.dto.BusPosition;
import az.yolcu.stream.StreamEvent;
import az.yolcu.user.Role;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BE-8.6: dashboard event feed (alerts table) + dashboard KPIs from the simulator and reviews. */
@Service
@Transactional
public class FeedService {
    static final int MAX_LIMIT = 200;
    // shortcut: no history of yesterday's speeds is kept, the mockup's value is used; replace when speeds are stored
    static final int AVG_SPEED_YESTERDAY = 22;
    /** design/web_dashboard.html FEED: category, title, body, minutes before the demo clock (08:12), target page. */
    static final List<Object[]> SEED = List.of(
            new Object[]{"HUB", "Koroğlu, platforma 2: 63 nəfər gözləyir", "Orta gözləmə 14 dəq. 15 dəqiqəyə 70-ə çata bilər", 1, "p-hubs"},
            new Object[]{"OCC", "88: avtobuslar 92% doludur", "Tövsiyə: interval 12 → 8 dəq", 2, "p-occ"},
            new Object[]{"OCC", "88: iki avtobus topa yığılıb", "10-BB-482 və 10-BB-511 arası 2 dəq", 4, "p-occ"},
            new Object[]{"AI", "33-dən 2 avtobusu 88-ə keçirin", "88-də doluluq 92%, 33-də 31%", 5, "p-split"},
            new Object[]{"BILETIM", "Gəncə reysi 08:20-də gəlir, 41 bilet satılıb", "M1 ekspress tövsiyə olunur", 6, "p-express"},
            new Object[]{"EVENT", "Bu axşam stadionda oyun var, 19:00", "65 və 88 üçün 17:30–20:30 arası +6 avtobus", 17, "p-occ"},
            new Object[]{"WEATHER", "17:00-dan yağış gözlənilir", "Avtobus tələbatı təxminən 15% artacaq", 22, "p-occ"});

    private final AlertRepository alerts;
    private final SimulationService sim;
    private final HubSimulator hubs;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final AppClock clock;

    public FeedService(AlertRepository alerts, SimulationService sim, HubSimulator hubs, JdbcTemplate jdbc,
                       ApplicationEventPublisher events, AppClock clock) {
        this.alerts = alerts;
        this.sim = sim;
        this.hubs = hubs;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedIfEmpty() {
        if (alerts.count() > 0) return;
        var now = clock.now().truncatedTo(ChronoUnit.SECONDS);
        // oldest first, so ids grow with time (the feed is ordered by id)
        for (int i = SEED.size() - 1; i >= 0; i--) {
            var r = SEED.get(i);
            alerts.save(new Alert((String) r[0], (String) r[1], (String) r[2], (String) r[4], now.minusMinutes((Integer) r[3])));
        }
    }

    // By id, not createdAt: the demo clock restarts at 08:12 on every boot, so createdAt can run backwards across restarts
    @Transactional(readOnly = true)
    public List<AlertDto> feed(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) throw ApiException.validation("limit: 1–" + MAX_LIMIT);
        return alerts.findAll(PageRequest.of(0, limit, Sort.by(Sort.Order.desc("id"))))
                .map(AlertDto::of).getContent();
    }

    /** Saves an alert and pushes alert.created to dispatchers and admins (after commit). */
    public AlertDto add(String category, String title, String body, String target) {
        var dto = AlertDto.of(alerts.save(new Alert(category, title, body, target, clock.now())));
        events.publishEvent(StreamEvent.toRole(Role.DISPATCHER, "alert.created", dto));
        events.publishEvent(StreamEvent.toRole(Role.ADMIN, "alert.created", dto));
        return dto;
    }

    /** Same transaction as the report, so a rolled-back report leaves no alert. */
    @EventListener
    public void onReportCreated(ReportCreatedEvent e) {
        var r = e.item();
        var body = r.textPreview() != null && !r.textPreview().isBlank() ? r.textPreview() : r.place();
        add("REPORT", "Yeni şikayət #" + r.code() + ": " + r.route() + ", " + r.category(), body, "p-reports");
    }

    @Transactional(readOnly = true)
    public Kpis kpis() {
        var buses = sim.buses(null);
        int avgOcc = (int) Math.round(buses.stream().mapToInt(BusPosition::occupancy).average().orElse(0));
        int overfull = (int) buses.stream().filter(b -> b.occupancy() > 90).count();
        var now = clock.now();
        Double all = jdbc.queryForObject("select avg((driver_rating + road_rating + bus_rating) / 3.0) from reviews", Double.class);
        String week = "select avg((driver_rating + road_rating + bus_rating) / 3.0) from reviews where created_at >= ? and created_at < ?";
        Double thisWeek = jdbc.queryForObject(week, Double.class, now.minusDays(7), now.plusDays(1));
        Double lastWeek = jdbc.queryForObject(week, Double.class, now.minusDays(14), now.minusDays(7));
        double delta = thisWeek != null && lastWeek != null ? round1(thisWeek - lastWeek) : 0;
        return new Kpis((int) Math.round(sim.avgSpeedKmh()), AVG_SPEED_YESTERDAY, hubs.totalWaiting(), avgOcc, overfull,
                all != null ? round1(all) : 0, delta);
    }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }
}
