package az.yolcu.seed;

import az.yolcu.common.AppClock;
import az.yolcu.config.YolcuProperties;
import az.yolcu.geo.StopService;
import az.yolcu.geo.dto.Stop;
import az.yolcu.user.UserSeed;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.sql.PreparedStatement;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** BE-1.3: fills an empty DB from mocks/*.json + fixed demo data. Skips if operators already exist. */
@Component
public class SeedRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private static final String DISPATCHER = "Leyla Məmmədova";
    private static final String[] NAMES = {"Rəşad Quliyev", "Aysel Hüseynova", "Elvin Məmmədov", "Günel Abbasova",
            "Murad İsmayılov", "Səbinə Kərimova", "Tural Həsənov", "Lalə Rzayeva", "Kamran Əhmədov", "Nərmin Babayeva",
            "Orxan Sadıqov", "Fidan Nəsirova", "Ramil Cəfərov", "Aytən Qasımova", "Vüsal Əsgərov", "Könül Mirzəyeva",
            "Fərid Səfərov", "Ülviyyə Hacıyeva", "Emin Bağırov", "Sevinc Talıbova"};

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final YolcuProperties props;
    private final AppClock clock;
    private final StopService stops;
    private final UserSeed userSeed;
    private final SyntheticGenerator generator;

    public SeedRunner(JdbcTemplate jdbc, ObjectMapper mapper, YolcuProperties props, AppClock clock,
                      StopService stops, UserSeed userSeed, SyntheticGenerator generator) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.props = props;
        this.clock = clock;
        this.stops = stops;
        this.userSeed = userSeed;
        this.generator = generator;
    }

    @Override
    @Transactional
    public void run(String... args) throws IOException {
        userSeed.seedAccounts();
        if (count("operators") > 0) return;
        long t0 = System.currentTimeMillis();
        var now = clock.now();
        var rnd = new Random(42);

        long demoId = jdbc.queryForObject("select min(id) from users where role = 'PASSENGER'", Long.class);
        var passengers = seedPassengers(rnd);
        var operatorIds = seedOperators();
        seedBuses(operatorIds);
        seedRouteConfig();
        seedSurveys(now, passengers, rnd);
        seedReports(now, operatorIds, passengers);
        seedDemoPassenger(now, demoId, operatorIds.get("bb"));
        generator.generate(now, passengers);

        log.info("Seed done in {} ms: users={} operators={} buses={} route_config={} surveys={} survey_answers={} "
                        + "reports={} report_events={} trips={} reviews={} coin_tx={}",
                System.currentTimeMillis() - t0, count("users"), count("operators"), count("buses"),
                count("route_config"), count("surveys"), count("survey_answers"), count("reports"),
                count("report_events"), count("trips"), count("reviews"), count("coin_tx"));
    }

    private List<Long> seedPassengers(Random rnd) {
        var ids = new ArrayList<Long>();
        for (int i = 0; i < NAMES.length; i++) {
            int balance = 20 * rnd.nextInt(20);
            long id = insert("insert into users (phone, full_name, role, coin_balance) values (?, ?, 'PASSENGER', ?)",
                    String.format("+994 50 %03d %02d %02d", 100 + i * 37, i, 10 + i), NAMES[i], balance);
            if (balance > 0) {
                jdbc.update("insert into coin_tx (user_id, amount, reason, ref_id, label) values (?, ?, 'SEED', 'seed', ?)",
                        id, balance, "Başlanğıc balansı");
            }
            ids.add(id);
        }
        return ids;
    }

    private Map<String, Long> seedOperators() throws IOException {
        var ids = new HashMap<String, Long>();
        for (var o : mock("operators.json").get("items")) {
            var routes = new ArrayList<String>();
            o.get("routes").forEach(r -> routes.add(r.asText()));
            ids.put(o.get("code").asText(), insert(
                    "insert into operators (code, name, fleet_total, punctuality, routes) values (?, ?, ?, ?, ?)",
                    o.get("code").asText(), o.get("name").asText(), o.get("fleetTotal").asInt(),
                    o.get("punctuality").asInt(), String.join(",", routes)));
        }
        return ids;
    }

    private void seedBuses(Map<String, Long> operatorIds) throws IOException {
        var rows = new ArrayList<Object[]>();
        for (var b : mock("buses.json")) {
            rows.add(new Object[]{b.get("bort").asText(), operatorIds.get(b.get("operator").asText()), b.get("route").asText(),
                    b.get("type").asText(), b.get("model").asText(), b.get("capacity").asInt(), b.get("status").asText()});
        }
        jdbc.batchUpdate("insert into buses (bort, operator_id, route, type, model, capacity, status) values (?, ?, ?, ?, ?, ?, ?)", rows);
    }

    private void seedRouteConfig() throws IOException {
        var rows = new ArrayList<Object[]>();
        for (var r : mock("route_config.json")) {
            int interval = r.get("intervalMin").asInt(), count = r.get("busCount").asInt();
            // shortcut: cycle time is not in the mock, interval × buses keeps headway = cycle / buses consistent
            rows.add(new Object[]{r.get("route").asText(), interval, count, interval * count});
        }
        jdbc.batchUpdate("insert into route_config (route, interval_min, bus_count, cycle_min) values (?, ?, ?, ?)", rows);
    }

    /** Historic totals go to seed_responses/seed_counts; the 20 synthetic passengers also leave real answers. */
    private void seedSurveys(OffsetDateTime now, List<Long> passengers, Random rnd) throws IOException {
        int i = 0;
        for (var s : mock("surveys.json")) {
            var status = s.get("status").asText();
            var created = now.minusDays(3L * ++i);
            long surveyId = insert("insert into surveys (title, status, audience, coin_reward, created_at, published_at, "
                            + "closed_at, seed_responses) values (?, ?, ?, ?, ?, ?, ?, ?)",
                    s.get("title").asText(), status, s.get("audience").asText(), s.get("coinReward").asInt(), created,
                    status.equals("DRAFT") ? null : created.plusHours(1),
                    status.equals("CLOSED") ? created.plusDays(2) : null, s.get("responses").asInt());

            var questions = new LinkedHashMap<Long, JsonNode>();
            int ord = 0;
            for (var q : s.get("questions")) {
                var options = new ArrayList<String>();
                var counts = new ArrayList<Long>();
                q.get("options").fields().forEachRemaining(e -> {
                    options.add(e.getKey());
                    counts.add(Math.round(s.get("responses").asInt() * e.getValue().asDouble() / 100));
                });
                questions.put(insert("insert into survey_questions (survey_id, ord, text, options, seed_counts) values (?, ?, ?, ?, ?)",
                        surveyId, ++ord, q.get("text").asText(), json(options), json(counts)), q.get("options"));
            }
            if (status.equals("DRAFT")) continue;
            var rows = new ArrayList<Object[]>();
            for (long userId : passengers) {
                var answers = new LinkedHashMap<String, String>();
                questions.forEach((qid, opts) -> answers.put(qid.toString(), weightedKey(opts, rnd)));
                rows.add(new Object[]{surveyId, userId, json(answers), created.plusHours(2 + rnd.nextInt(40))});
            }
            jdbc.batchUpdate("insert into survey_answers (survey_id, user_id, answers, created_at) values (?, ?, ?, ?)", rows);
        }
    }

    private void seedReports(OffsetDateTime now, Map<String, Long> operatorIds, List<Long> passengers) throws IOException {
        var answer = "Operator sürücü ilə izahat işi apardı, təkrarlanmaması üçün nəzarət gücləndirildi.";
        var events = new ArrayList<Object[]>();
        int i = 0;
        for (var r : mock("reports_seed.json")) {
            var status = r.get("status").asText();
            long ageMin = r.get("minutesAgo").asLong();
            // late = FORWARDED > 24 h ago, so those are moved a day back to keep the timeline consistent
            if (r.path("late").asBoolean()) ageMin += 25 * 60;
            var created = now.minusMinutes(ageMin);
            var review = created.plusSeconds(ageMin * 12);    // 20% of the age
            var forwarded = created.plusSeconds(ageMin * 24); // 40%
            var closed = created.plusSeconds(ageMin * 48);    // 80%
            if (r.path("late").asBoolean()) {
                review = created.plusMinutes(10); // keep REVIEW before FORWARDED in the timeline
                forwarded = created.plusMinutes(30);
            }
            boolean isForwarded = List.of("FORWARDED", "ANSWERED", "RESOLVED").contains(status);
            boolean isClosed = List.of("ANSWERED", "RESOLVED").contains(status);
            var updated = isClosed ? closed : isForwarded ? forwarded : status.equals("REVIEW") ? review : created;
            var stop = stops.all().stream().filter(s -> s.name().startsWith(r.get("place").asText())).findFirst();

            long id = insert("insert into reports (code, user_id, route, bort, operator_id, category, text, has_photo, place, "
                            + "lat, lon, status, answer_text, created_at, forwarded_at, updated_at) "
                            + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    r.get("code").asText(), passengers.get(i++ % passengers.size()), r.get("route").asText(),
                    r.get("bort").asText(), operatorIds.get(r.get("operator").asText()), r.get("category").asText(),
                    r.get("text").asText(), r.get("hasPhoto").asBoolean(), r.get("place").asText(),
                    stop.map(Stop::lat).orElse(null), stop.map(Stop::lon).orElse(null), status,
                    isClosed ? answer : null, created, isForwarded ? forwarded : null, updated);

            events.add(new Object[]{id, "CREATED", null, "Sərnişin", created});
            if (!status.equals("NEW")) events.add(new Object[]{id, "STATUS", "REVIEW", DISPATCHER, review});
            if (isForwarded) events.add(new Object[]{id, "FORWARDED", "Operatora göndərildi", DISPATCHER, forwarded});
            if (status.equals("ANSWERED")) events.add(new Object[]{id, "ANSWERED", answer, "Operator", closed});
            if (status.equals("RESOLVED")) events.add(new Object[]{id, "STATUS", "RESOLVED", DISPATCHER, closed});
        }
        jdbc.batchUpdate("insert into report_events (report_id, type, note, actor, created_at) values (?, ?, ?, ?, ?)", events);
    }

    /** Habit data for PatternService: 12 weekday mornings 88 Elmlər → Nərimanov + 5 evenings 65 Gənclik → Elmlər. */
    private void seedDemoPassenger(OffsetDateTime now, long userId, long bakuBusId) {
        var nerimanov = stops.byId("nerimanov").orElseThrow();
        var elmler = stops.byId("elmler").orElseThrow();
        var today = now.truncatedTo(ChronoUnit.DAYS);
        var rnd = new Random(7);
        var sql = "insert into trips (user_id, route, bort, from_stop, to_place, alight_stop, alight_lat, alight_lon, "
                + "alight_detected, paid_with, started_at, ended_at) values (?, ?, ?, ?, ?, ?, ?, ?, 'AUTO', ?, ?, ?)";
        long reviewTrip = 0, fareTrip = 0;
        OffsetDateTime reviewAt = null, fareAt = null;
        int mornings = 0, evenings = 0;
        for (int d = 1; d <= 21 && mornings < 12; d++) {
            var day = today.minusDays(d);
            if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            var start = day.plusHours(8).plusMinutes(5 + rnd.nextInt(16));
            boolean fare = fareTrip == 0 && d >= 3;
            long id = insert(sql, userId, "88", "10-BB-482", "elmler", "nerimanov", nerimanov.name(), nerimanov.lat(),
                    nerimanov.lon(), fare ? "COIN" : "BAKIKART", start, start.plusMinutes(24));
            mornings++;
            if (fare) { fareTrip = id; fareAt = start; }
            else if (reviewTrip == 0 && d >= 2) { reviewTrip = id; reviewAt = start.plusMinutes(30); }
            if (evenings < 5) {
                var eve = day.plusHours(18).plusMinutes(20 + rnd.nextInt(20));
                insert(sql, userId, "65", "10-BB-120", "genclik", "elmler", elmler.name(), elmler.lat(), elmler.lon(),
                        "BAKIKART", eve, eve.plusMinutes(28));
                evenings++;
            }
        }
        jdbc.update("insert into reviews (trip_id, user_id, route, bort, operator_id, driver_rating, road_rating, bus_rating, "
                        + "crowd_level, tags, created_at) values (?, ?, '88', '10-BB-482', ?, 5, 4, 5, 'FULL', 'Nəzakətli sürücü', ?)",
                reviewTrip, userId, bakuBusId, reviewAt);

        // wallet history as in design/mobile_app.html; the SEED row makes the ledger sum equal the balance (245)
        var coin = "insert into coin_tx (user_id, amount, reason, ref_id, label, created_at) values (?, ?, ?, ?, ?, ?)";
        jdbc.update(coin, userId, 275, "SEED", "seed", "Başlanğıc balansı", today.minusDays(22));
        jdbc.update(coin, userId, -60, "FARE", String.valueOf(fareTrip), "Gediş haqqı, coin ilə", fareAt);
        jdbc.update(coin, userId, 5, "REVIEW", String.valueOf(reviewTrip), "Səfər rəyi, 88", reviewAt);
        jdbc.update(coin, userId, 15, "REPORT_VERIFIED", "A-2031", "Təsdiqlənmiş şikayət #A-2031", today.minusDays(2).plusHours(15));
        jdbc.update(coin, userId, 10, "SURVEY", "seed-interval", "Sorğu: interval rahatlığı", today.minusDays(1).plusHours(19));
        jdbc.update("update users set coin_balance = 245 where id = ?", userId);
    }

    static String weightedKey(JsonNode weights, Random rnd) {
        double total = 0;
        for (var w : weights) total += w.asDouble();
        double x = rnd.nextDouble() * total;
        var it = weights.fields();
        String key = null;
        while (it.hasNext()) {
            var e = it.next();
            key = e.getKey();
            if ((x -= e.getValue().asDouble()) < 0) break;
        }
        return key;
    }

    private long insert(String sql, Object... args) {
        var keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            new ArgumentPreparedStatementSetter(args).setValues(ps);
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }

    private long count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Long.class);
    }

    private JsonNode mock(String file) throws IOException {
        return mapper.readTree(props.mocksPath().resolve(file).toFile());
    }

    private String json(Object o) throws IOException {
        return mapper.writeValueAsString(o);
    }
}
