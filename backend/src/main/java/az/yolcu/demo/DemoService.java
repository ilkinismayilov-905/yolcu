package az.yolcu.demo;

import az.yolcu.common.ApiException;
import az.yolcu.common.AppClock;
import az.yolcu.config.YolcuProperties;
import az.yolcu.demo.dto.DemoResult;
import az.yolcu.demo.dto.ResetResult;
import az.yolcu.feed.FeedService;
import az.yolcu.notification.NotificationService;
import az.yolcu.occupancy.OccupancyService;
import az.yolcu.proposal.HubProposalRule;
import az.yolcu.proposal.HubProposalService;
import az.yolcu.report.ReportService;
import az.yolcu.report.dto.CreateReportRequest;
import az.yolcu.sim.HubSimulator;
import az.yolcu.sim.SimulationService;
import az.yolcu.stream.StreamEvent;
import az.yolcu.traffic.TrafficService;
import az.yolcu.user.Role;
import az.yolcu.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** BE-9.5: demo scenarios (03_API_CONTRACT §10) and reset to the boot state. */
@Service
public class DemoService {
    static final String JAM_SEGMENT = "babek", BUNCH_ROUTE = "88", OVERLOAD_ROUTE = "88", CROWD_HUB = "may28";
    static final int JAM_MINUTES = 35, OVERLOAD_PCT = 95, DEMO_BALANCE = 245;
    static final long SLOW_AT_MS = 1_000, JAM_AT_MS = 2_500, RULE_AT_MS = 3_000;
    static final double CROWD_FACTOR = 70 / 46.0; // contract: "46 → 70"
    private static final Logger log = LoggerFactory.getLogger(DemoService.class);
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    /** Runs a step after a delay; tests replace it to run steps without sleeping. */
    interface Delayer { void later(long ms, Runnable step); }

    private final TrafficService traffic;
    private final HubProposalRule rule;
    private final HubSimulator hubs;
    private final SimulationService sim;
    private final OccupancyService occupancy;
    private final FeedService feed;
    private final ReportService reports;
    private final NotificationService notifications;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;
    private final AppClock clock;
    private final ObjectMapper mapper;
    private final YolcuProperties props;
    private final AtomicInteger epoch = new AtomicInteger(); // reset bumps it, pending jam steps of an older epoch are dropped
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        var t = new Thread(r, "demo-steps");
        t.setDaemon(true);
        return t;
    });
    Delayer delayer = (ms, step) -> executor.schedule(step, ms, TimeUnit.MILLISECONDS);

    public DemoService(TrafficService traffic, HubProposalRule rule, HubSimulator hubs, SimulationService sim,
                       OccupancyService occupancy, FeedService feed, ReportService reports, NotificationService notifications,
                       UserRepository users, JdbcTemplate jdbc, TransactionTemplate tx, ApplicationEventPublisher events,
                       AppClock clock, ObjectMapper mapper, YolcuProperties props) {
        this.traffic = traffic;
        this.rule = rule;
        this.hubs = hubs;
        this.sim = sim;
        this.occupancy = occupancy;
        this.feed = feed;
        this.reports = reports;
        this.notifications = notifications;
        this.users = users;
        this.jdbc = jdbc;
        this.tx = tx;
        this.events = events;
        this.clock = clock;
        this.mapper = mapper;
        this.props = props;
    }

    public DemoResult trigger(String scenario) {
        log.info("Demo trigger: {}", scenario);
        var message = switch (scenario) {
            case "artificial-jam" -> artificialJam();
            case "new-report" -> newReport();
            case "overload-88" -> overload();
            case "bunching" -> bunching();
            case "crowd-28may" -> crowd();
            case "stadium" -> stadium();
            case "rain" -> {
                feed.add("WEATHER", "17:00-dan yağış gözlənilir", "Avtobus tələbatı təxminən 15% artacaq", "p-occ");
                yield "WEATHER xəbərdarlığı lentə yazıldı";
            }
            case "ganja-arrival" -> {
                var at = clock.now().plusMinutes(5).atZoneSameInstant(AppClock.ZONE).format(HHMM);
                feed.add("BILETIM", "Gəncə reysi " + at + "-də gəlir, 45 bilet satılıb", "M1 ekspress tövsiyə olunur", "p-express");
                yield "BILETIM xəbərdarlığı: Gəncə reysi " + at + "-də, 45 bilet";
            }
            default -> throw ApiException.notFound("Ssenari tapılmadı: " + scenario);
        };
        return new DemoResult(true, scenario, message);
    }

    /** Fətəli Xan Xoyski pr. (segment id "babek", kept for stable ids): SLOW after 1 s, JAM (35 min) after 2.5 s, HubProposalRule after 3 s → proposal.created. */
    private String artificialJam() {
        int e = epoch.get();
        delayer.later(SLOW_AT_MS, step(e, () -> {
            traffic.force(JAM_SEGMENT, "SLOW");
            feed.add("EVENT", name() + ": hərəkət ləngiyir", "Orta sürət " + speed() + " km/s", "p-map");
        }));
        delayer.later(JAM_AT_MS, step(e, () -> {
            traffic.force(JAM_SEGMENT, "JAM");
            traffic.setJamMinutes(JAM_SEGMENT, JAM_MINUTES);
            feed.add("OCC", name() + ": tıxac, " + JAM_MINUTES + " dəqiqədir davam edir", "Orta sürət " + speed() + " km/s", "p-map");
        }));
        delayer.later(RULE_AT_MS, step(e, () -> {
            var created = rule.check(true);
            if (created.isEmpty()) log.warn("Demo artificial-jam: HubProposalRule created no proposal");
        }));
        return name() + ": 1 san sonra SLOW, 2,5 san sonra JAM (" + JAM_MINUTES + " dəq), 3 san sonra hub təklifi qaydası";
    }

    private String newReport() {
        long uid = demoPassenger();
        var stop = new double[]{40.38, 49.8489}; // 28 May stop (mocks/stops_seed.json)
        var r = reports.create(uid, new CreateReportRequest("88", "10-BB-482", "Kobud davranış",
                "Sürücü 28 May dayanacağında qapını gec açdı və sərnişinlərlə kobud danışdı.", false, stop[0], stop[1]));
        return "Şikayət #" + r.report().code() + " demo sərnişin adından yaradıldı";
    }

    private String overload() {
        var r = occupancy.demoOverload(OVERLOAD_ROUTE, OVERLOAD_PCT);
        if (r == null) throw ApiException.notFound(OVERLOAD_ROUTE + " simulyasiya olunmur");
        return OVERLOAD_ROUTE + " doluluğu " + r.avgOccupancy() + "%, tövsiyə " + r.intervalMin() + " → " + r.recommendedMin() + " dəq";
    }

    private String bunching() {
        var b = occupancy.demoBunching(BUNCH_ROUTE);
        if (b == null) throw ApiException.notFound(BUNCH_ROUTE + " marşrutunda topa yığılma yaradıla bilmədi");
        return b.front().bort() + " və " + b.back().bort() + " arası " + b.gapMin() + " dəq";
    }

    private String crowd() {
        if (!hubs.boost(CROWD_HUB, CROWD_FACTOR)) throw ApiException.notFound("Hub tapılmadı: " + CROWD_HUB);
        var h = hubs.hubs().stream().filter(x -> x.id().equals(CROWD_HUB)).findFirst().orElseThrow();
        feed.add("HUB", h.name() + ": " + h.count() + " nəfər gözləyir", "15 dəqiqəyə " + h.forecast15() + "-ə çata bilər", "p-hubs");
        return h.name() + " hub sayı " + h.count() + " (×70/46)";
    }

    private String stadium() {
        feed.add("EVENT", "Bu axşam stadionda oyun var, 19:00", "65 və 88 üçün 17:30–20:30 arası +6 avtobus", "p-occ");
        var ids = jdbc.queryForList("select id from users where role = 'PASSENGER' and synthetic = false order by id", Long.class);
        int n = notifications.createAll(ids, "INFO", "Bu axşam stadionda oyun var, 19:00",
                "65 və 88 marşrutlarında 17:30–20:30 arası əlavə avtobuslar işləyəcək. Çıxışa 30 dəqiqə tez çıxın.", "event:stadium");
        return "EVENT xəbərdarlığı və " + n + " sərnişinə bildiriş";
    }

    /**
     * Simulator back to boot state; DB: today's (demo clock) rows of the demo passenger, the alerts her reports made,
     * all hub proposals (+ their AI alerts); balance 245; route_config from mocks/route_config.json. One transaction.
     */
    public ResetResult reset() {
        epoch.incrementAndGet();
        long uid = demoPassenger();
        var today = clock.now().atZoneSameInstant(AppClock.ZONE).truncatedTo(ChronoUnit.DAYS).toOffsetDateTime();
        var deleted = tx.execute(s -> {
            var d = new LinkedHashMap<String, Integer>();
            var codes = jdbc.queryForList("select code from reports where user_id = ? and created_at >= ?", String.class, uid, today);
            int alerts = 0;
            for (var code : codes) alerts += jdbc.update("delete from alerts where category = 'REPORT' and title like ?", "Yeni şikayət #" + code + ":%");
            d.put("report_events", jdbc.update("delete from report_events where report_id in "
                    + "(select id from reports where user_id = ? and created_at >= ?)", uid, today));
            d.put("reports", jdbc.update("delete from reports where user_id = ? and created_at >= ?", uid, today));
            d.put("reviews", jdbc.update("delete from reviews where (user_id = ? and created_at >= ?) or trip_id in "
                    + "(select id from trips where user_id = ? and started_at >= ?)", uid, today, uid, today));
            d.put("trips", jdbc.update("delete from trips where user_id = ? and started_at >= ?", uid, today));
            d.put("coin_tx", jdbc.update("delete from coin_tx where user_id = ? and created_at >= ?", uid, today));
            d.put("coin_conversions", jdbc.update("delete from coin_conversions where user_id = ? and created_at >= ?", uid, today));
            d.put("notifications", jdbc.update("delete from notifications where user_id = ? and created_at >= ?", uid, today));
            d.put("survey_answers", jdbc.update("delete from survey_answers where user_id = ? and created_at >= ?", uid, today));
            alerts += jdbc.update("delete from alerts where category = 'AI' and title like ?", HubProposalService.ALERT_PREFIX + "%");
            d.put("alerts", alerts);
            d.put("hub_proposals", jdbc.update("delete from hub_proposals"));
            jdbc.update("update users set coin_balance = ? where id = ?", DEMO_BALANCE, uid);
            jdbc.update("update route_config set auto_mode = false");
            for (var r : routeConfig()) {
                jdbc.update("update route_config set interval_min = ?, bus_count = ?, updated_at = current_timestamp where route = ?",
                        r.path("intervalMin").asInt(), r.path("busCount").asInt(), r.path("route").asText());
            }
            return d;
        });
        traffic.reset();
        hubs.reset();
        occupancy.resetState();
        sim.reset();
        events.publishEvent(StreamEvent.toUser(uid, "coin.updated", Map.of("balance", DEMO_BALANCE)));
        log.info("Demo reset: deleted {}", deleted);
        return new ResetResult(true, "reset", "Simulyator və demo data başlanğıc vəziyyətinə qaytarıldı", DEMO_BALANCE, deleted);
    }

    private Runnable step(int e, Runnable r) {
        return () -> {
            if (epoch.get() != e) return; // reset happened meanwhile
            try {
                r.run();
            } catch (RuntimeException ex) {
                log.error("Demo step failed", ex);
            }
        };
    }

    private long demoPassenger() {
        return users.findFirstByRoleOrderByIdAsc(Role.PASSENGER).orElseThrow(() -> ApiException.notFound("Demo sərnişin yoxdur")).getId();
    }

    private Iterable<com.fasterxml.jackson.databind.JsonNode> routeConfig() {
        try {
            return mapper.readTree(props.mocksPath().resolve("route_config.json").toFile());
        } catch (IOException e) {
            throw new IllegalStateException("mocks/route_config.json", e);
        }
    }

    private String name() { return segment().map(s -> s.name()).orElse("Fətəli Xan Xoyski pr."); }

    private int speed() { return segment().map(s -> s.speedKmh()).orElse(0); }

    private java.util.Optional<az.yolcu.traffic.dto.TrafficSegment> segment() {
        return traffic.segments().stream().filter(s -> s.id().equals(JAM_SEGMENT)).findFirst();
    }
}
