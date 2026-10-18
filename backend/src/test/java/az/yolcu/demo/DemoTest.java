package az.yolcu.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.feed.dto.AlertDto;
import az.yolcu.occupancy.OccupancyService;
import az.yolcu.security.JwtService;
import az.yolcu.sim.SimulationService;
import az.yolcu.stream.StreamEvent;
import az.yolcu.traffic.TrafficService;
import az.yolcu.user.Role;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Jam steps are collected by a fake Delayer and run in order, no sleeping. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@RecordApplicationEvents
class DemoTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ApplicationEvents events;
    @Autowired DemoService demo;
    @Autowired DemoController controller;
    @Autowired SimulationService sim;
    @Autowired OccupancyService occupancy;
    @Autowired TrafficService traffic;

    final List<long[]> delays = new ArrayList<>();
    final List<Runnable> steps = new ArrayList<>();
    DemoService.Delayer original;
    long nigar;

    String auth() {
        return "Bearer " + jwt.issue(jdbc.queryForObject("select id from users where email = 'dispatcher@yolcu.az'", Long.class), "DISPATCHER");
    }

    ResultActions trigger(String scenario) throws Exception {
        return mvc.perform(post("/api/v1/demo/trigger/" + scenario).header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.scenario").value(scenario))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    List<String> alerts(String category) {
        return events.stream(StreamEvent.class).filter(e -> e.name().equals("alert.created") && e.role() == Role.DISPATCHER).map(e -> (AlertDto) e.data())
                .filter(a -> a.category().equals(category)).map(AlertDto::title).toList();
    }

    long count(String sql, Object... args) { return jdbc.queryForObject(sql, Long.class, args); }

    @BeforeEach
    void setUp() throws Exception {
        original = demo.delayer;
        demo.delayer = (ms, step) -> { delays.add(new long[]{ms}); steps.add(step); };
        nigar = jdbc.queryForObject("select min(id) from users where role = 'PASSENGER'", Long.class);
        for (int i = 0; i < 5; i++) sim.step();
        mvc.perform(post("/api/v1/demo/reset").header("Authorization", auth())).andExpect(status().isOk());
        events.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        mvc.perform(post("/api/v1/demo/reset").header("Authorization", auth()));
        demo.delayer = original;
        controller.enabled = true;
        jdbc.update("delete from notifications where target = 'event:stadium'");
        jdbc.update("delete from alerts where id > 7");
    }

    @Test
    void artificialJamThenProposal() throws Exception {
        trigger("artificial-jam");
        assertThat(delays).extracting(d -> d[0]).containsExactly(1_000L, 2_500L, 3_000L);
        assertThat(level()).isEqualTo("FREE");
        steps.get(0).run();
        assertThat(level()).isEqualTo("SLOW");
        steps.get(1).run();
        assertThat(level()).isEqualTo("JAM");
        steps.get(2).run();
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("traffic.updated")).count()).isEqualTo(2);
        assertThat(alerts("OCC")).contains("Fətəli Xan Xoyski pr.: tıxac, 35 dəqiqədir davam edir");
        assertThat(alerts("AI")).contains("Yeni hub təklifi: Fətəli Xan Xoyski pr. – Gənclik metrosu");
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("proposal.created") && e.role() == Role.DISPATCHER).count()).isEqualTo(1);
        mvc.perform(get("/api/v1/admin/hub-proposals").header("Authorization", auth()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].jamMinutes").value(35));
    }

    @Test
    void stepsAfterResetAreDropped() throws Exception {
        trigger("artificial-jam");
        mvc.perform(post("/api/v1/demo/reset").header("Authorization", auth()));
        steps.forEach(Runnable::run);
        assertThat(level()).isEqualTo("FREE");
        assertThat(count("select count(*) from hub_proposals")).isZero();
    }

    @Test
    void newReportAsDemoPassenger() throws Exception {
        long before = count("select count(*) from reports where user_id = ?", nigar);
        trigger("new-report");
        assertThat(count("select count(*) from reports where user_id = ?", nigar)).isEqualTo(before + 1);
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("report.created")).count()).isGreaterThan(0);
        assertThat(alerts("REPORT")).hasSize(1).allMatch(t -> t.startsWith("Yeni şikayət #A-") && t.endsWith(": 88, Kobud davranış"));
    }

    @Test
    void overloadAndBunching88() throws Exception {
        trigger("overload-88");
        mvc.perform(get("/api/v1/admin/occupancy/routes").header("Authorization", auth()))
                .andExpect(jsonPath("$[0].route").value("88"))
                .andExpect(jsonPath("$[0].avgOccupancy").value(org.hamcrest.Matchers.greaterThanOrEqualTo(90)));
        assertThat(alerts("OCC")).anyMatch(t -> t.startsWith("88: avtobuslar 9"));

        // open the boot gap (driver waits), then the trigger bunches the pair again — every time
        occupancy.message("88");
        for (int i = 0; i < 120; i++) sim.step();
        assertThat(occupancy.bunching()).noneMatch(b -> b.route().equals("88"));
        for (int round = 1; round <= 2; round++) {
            trigger("bunching");
            mvc.perform(get("/api/v1/admin/occupancy/bunching").header("Authorization", auth()))
                    .andExpect(jsonPath("$[?(@.route == '88')].gapMin").value(org.hamcrest.Matchers.contains(2)));
            assertThat(alerts("OCC").stream().filter(t -> t.equals("88: iki avtobus topa yığılıb"))).hasSize(round);
        }
    }

    @Test
    void feedScenarios() throws Exception {
        int before = hubCount("may28");
        trigger("crowd-28may");
        assertThat(hubCount("may28")).isGreaterThan(before * 14 / 10);
        trigger("stadium");
        trigger("rain");
        trigger("ganja-arrival");
        assertThat(alerts("HUB")).anyMatch(t -> t.startsWith("28 May: "));
        assertThat(alerts("EVENT")).contains("Bu axşam stadionda oyun var, 19:00");
        assertThat(alerts("WEATHER")).contains("17:00-dan yağış gözlənilir");
        assertThat(alerts("BILETIM")).anyMatch(t -> t.startsWith("Gəncə reysi ") && t.endsWith("45 bilet satılıb"));
        assertThat(count("select count(*) from notifications where target = 'event:stadium' and user_id = ?", nigar)).isEqualTo(1);
        mvc.perform(post("/api/v1/demo/trigger/nope").header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetRestoresDemoState() throws Exception {
        long seededTrips = count("select count(*) from trips where user_id = ?", nigar);
        long seededTx = count("select count(*) from coin_tx where user_id = ?", nigar);
        long reportsTotal = count("select count(*) from reports where user_id <> ?", nigar);
        long synthetic = count("select count(*) from trips where synthetic = true");
        var now = OffsetDateTime.now();
        jdbc.update("insert into trips (user_id, route, from_stop, paid_with, started_at) values (?, '88', 'elmler', 'COIN', ?)", nigar, now);
        trigger("new-report");
        trigger("stadium");
        trigger("artificial-jam");
        steps.forEach(Runnable::run);
        // a survey created during the demo session (published → notifications) must go too; the 7 seeded alerts stay
        mvc.perform(post("/api/v1/admin/surveys").header("Authorization", auth()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Demo sorğu\",\"audience\":\"ALL\",\"coinReward\":5,\"publish\":true,"
                                + "\"questions\":[{\"text\":\"Lazımdır?\",\"options\":[\"Bəli\",\"Xeyr\"]}]}"))
                .andExpect(status().isCreated());
        long surveysBefore = count("select count(*) from surveys where title <> 'Demo sorğu'");
        jdbc.update("update route_config set interval_min = 8, auto_mode = true where route = '88'");
        jdbc.update("update users set coin_balance = 9 where id = ?", nigar);

        mvc.perform(post("/api/v1/demo/reset").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.coinBalance").value(245))
                .andExpect(jsonPath("$.deleted.reports").value(1))
                .andExpect(jsonPath("$.deleted.trips").value(1))
                .andExpect(jsonPath("$.deleted.hub_proposals").value(1));
        assertThat(count("select coin_balance from users where id = ?", nigar)).isEqualTo(245);
        assertThat(count("select count(*) from trips where user_id = ?", nigar)).isEqualTo(seededTrips);
        assertThat(count("select count(*) from coin_tx where user_id = ?", nigar)).isEqualTo(seededTx);
        assertThat(count("select count(*) from reports where user_id <> ?", nigar)).isEqualTo(reportsTotal);
        assertThat(count("select count(*) from trips where synthetic = true")).isEqualTo(synthetic);
        assertThat(count("select count(*) from notifications where user_id = ? and target = 'event:stadium'", nigar)).isZero();
        assertThat(count("select count(*) from notifications where user_id <> ? and target = 'event:stadium'", nigar)).isPositive();
        assertThat(count("select count(*) from hub_proposals")).isZero();
        assertThat(count("select count(*) from alerts where title like 'Yeni hub təklifi:%' or title like 'Yeni şikayət #%'")).isZero();
        assertThat(count("select count(*) from surveys where title = 'Demo sorğu'")).isZero();
        assertThat(count("select count(*) from surveys")).isEqualTo(surveysBefore);
        assertThat(count("select count(*) from notifications where type = 'SURVEY' and title = 'Demo sorğu'")).isZero();
        assertThat(count("select count(*) from alerts where id <= 7")).isEqualTo(7); // seeded feed survives
        assertThat(count("select interval_min from route_config where route = '88'")).isEqualTo(12);
        assertThat(count("select count(*) from route_config where auto_mode = true")).isZero();
        assertThat(level()).isEqualTo("FREE");
        assertThat(occupancy.bunching()).anyMatch(b -> b.route().equals("88")); // boot spacing: 10-BB-511 2 min behind
    }

    @Test
    void gateAndRoles() throws Exception {
        mvc.perform(post("/api/v1/demo/trigger/rain").header("Authorization", "Bearer " + jwt.issue(nigar, "PASSENGER")))
                .andExpect(status().isForbidden());
        controller.enabled = false;
        mvc.perform(post("/api/v1/demo/trigger/rain").header("Authorization", auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(post("/api/v1/demo/reset").header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }

    String level() {
        return traffic.segments().stream().filter(s -> s.id().equals("babek")).findFirst().orElseThrow().level();
    }

    int hubCount(String id) throws Exception {
        var body = mvc.perform(get("/api/v1/live/hubs").header("Authorization", auth())).andReturn().getResponse().getContentAsString();
        var m = java.util.regex.Pattern.compile("\"id\":\"" + id + "\".*?\"count\":(\\d+)").matcher(body);
        assertThat(m.find()).isTrue();
        return Integer.parseInt(m.group(1));
    }
}
