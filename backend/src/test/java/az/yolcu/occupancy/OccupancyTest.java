package az.yolcu.occupancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.feed.dto.AlertDto;
import az.yolcu.occupancy.dto.RouteOccupancy;
import az.yolcu.security.JwtService;
import az.yolcu.sim.SimulationService;
import az.yolcu.sim.dto.BusPosition;
import az.yolcu.stream.StreamEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

/** Scheduling is off (application-h2.yml): sim.step(), autoTick() and bunchingTick() are called directly. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@RecordApplicationEvents
class OccupancyTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ApplicationEvents events;
    @Autowired SimulationService sim;
    @Autowired OccupancyService occupancy;

    String auth() {
        return "Bearer " + jwt.issue(jdbc.queryForObject("select id from users where email = 'dispatcher@yolcu.az'", Long.class), "DISPATCHER");
    }

    @BeforeEach
    void warmUp() { for (int i = 0; i < 20; i++) sim.step(); }

    @AfterEach
    void restore() {
        jdbc.update("update route_config set auto_mode = false");
        jdbc.update("update route_config set interval_min = 12 where route in ('88', '65', '33', 'H1')");
        jdbc.update("update route_config set interval_min = 10 where route in ('4', 'M1')");
        jdbc.update("delete from alerts where id > 7"); // keep the 7 seeded ones (FeedTest)
        occupancy.resetState();
        sim.step();
    }

    @Test
    void routesListFromLiveSim() throws Exception {
        mvc.perform(get("/api/v1/admin/occupancy/routes").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5)) // H1 has no borts on LINE → not simulated
                .andExpect(jsonPath("$[0].route").value("88"))
                .andExpect(jsonPath("$[0].type").value("ELECTRIC"))
                .andExpect(jsonPath("$[0].operator").value("BakuBus MMC"))
                .andExpect(jsonPath("$[0].busCount").value(6))
                .andExpect(jsonPath("$[0].intervalMin").value(12))
                // sim base 88 % sits near the 8/9 threshold (12 × 65 / occ), noise flips it
                .andExpect(jsonPath("$[0].recommendedMin").value(org.hamcrest.Matchers.oneOf(8, 9)))
                .andExpect(jsonPath("$[0].autoMode").value(false));
        mvc.perform(get("/api/v1/admin/occupancy/routes/88/buses").header("Authorization", auth()))
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].bort").value("10-BB-482"))
                .andExpect(jsonPath("$[0].capacity").value(90))
                .andExpect(jsonPath("$[0].near").isNotEmpty());
        mvc.perform(get("/api/v1/admin/occupancy/simulate?route=88&intervalMin=8").header("Authorization", auth()))
                .andExpect(jsonPath("$.waitMin").value(4.0))
                .andExpect(jsonPath("$.busesNeeded").value(9))
                .andExpect(jsonPath("$.note").value(org.hamcrest.Matchers.startsWith("3 əlavə avtobus lazımdır. Təklif: 33-dən 2, depodan 1")));
        mvc.perform(get("/api/v1/admin/occupancy/routes/nope/buses").header("Authorization", auth()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void applyIntervalLowersOccupancy() throws Exception {
        double before = avg88();
        mvc.perform(post("/api/v1/admin/occupancy/routes/88/interval").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"intervalMin\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.route").value("88"))
                .andExpect(jsonPath("$.intervalMin").value(8))
                // occupancy still ~88 % right after the change → engine says 6 (8 × 65 / 88); never overwritten
                .andExpect(jsonPath("$.recommendedMin").value(6));
        assertThat(jdbc.queryForObject("select interval_min from route_config where route = '88'", Integer.class)).isEqualTo(8);
        var updated = events.stream(StreamEvent.class).filter(e -> e.name().equals("route.updated")).toList();
        assertThat(updated).hasSize(1);
        assertThat(((RouteOccupancy) updated.get(0).data()).intervalMin()).isEqualTo(8);
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("alert.created"))
                .map(e -> (AlertDto) e.data()).toList())
                .anyMatch(a -> a.category().equals("AI") && a.title().equals("88: interval 12 → 8 dəq tətbiq olundu"));
        for (int i = 0; i < 15; i++) sim.step(); // 30 s
        assertThat(avg88()).isLessThan(before - 15).isBetween(45.0, 75.0); // base 88 % × 8/12 ≈ 59 %
        jdbc.update("update route_config set auto_mode = true");
        occupancy.autoTick(); // 88 was changed < 60 s ago → auto mode leaves it alone
        assertThat(jdbc.queryForObject("select interval_min from route_config where route = '88'", Integer.class)).isEqualTo(8);
    }

    @Test
    void invalidInterval() throws Exception {
        for (var body : new String[]{"{\"intervalMin\":5}", "{\"intervalMin\":17}", "{}"}) {
            mvc.perform(post("/api/v1/admin/occupancy/routes/88/interval").header("Authorization", auth())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        }
        mvc.perform(get("/api/v1/admin/occupancy/simulate?route=88&intervalMin=30").header("Authorization", auth()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void autoModeAppliesRecommendation() throws Exception {
        mvc.perform(put("/api/v1/admin/occupancy/auto").header("Authorization", auth())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(true));
        int rec = occupancy.route("88").recommendedMin();
        assertThat(rec).isBetween(8, 9);
        occupancy.autoTick();
        assertThat(jdbc.queryForObject("select interval_min from route_config where route = '88'", Integer.class)).isEqualTo(rec);
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("route.updated"))
                .map(e -> ((RouteOccupancy) e.data()).route())).contains("88");
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("alert.created"))
                .map(e -> ((AlertDto) e.data()).title())).contains("88: interval 12 → " + rec + " dəq (avtomatik rejim)");
    }

    @Test
    void bunchingDetectedAndMessageOpensGap() throws Exception {
        // 88 starts with 10-BB-511 2 min behind 10-BB-482 (demo spacing)
        mvc.perform(get("/api/v1/admin/occupancy/bunching").header("Authorization", auth()))
                .andExpect(jsonPath("$[0].route").value("88"))
                .andExpect(jsonPath("$[0].front.bort").value(org.hamcrest.Matchers.oneOf("10-BB-482", "10-BB-511")))
                .andExpect(jsonPath("$[0].gapMin").value(2))
                .andExpect(jsonPath("$[0].near").isNotEmpty());
        var pair = occupancy.bunching().get(0);
        assertThat(pair.front().occupancy()).isGreaterThanOrEqualTo(pair.back().occupancy());
        occupancy.bunchingTick(); // bunching present at boot is already in the seeded feed → no alert
        assertThat(occAlerts()).isEmpty();
        mvc.perform(post("/api/v1/admin/occupancy/bunching/88/message").header("Authorization", auth()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true))
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.startsWith("10-BB-511 sürücüsünə: ")));
        for (int i = 0; i < 40; i++) sim.step(); // back bus waits 80 s: gap 2 → 3.3 min
        mvc.perform(get("/api/v1/admin/occupancy/bunching").header("Authorization", auth()))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/v1/admin/occupancy/bunching/88/message").header("Authorization", auth()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        occupancy.bunchingTick();
        // close the gap again: release the back bus, stop the front one for 1 min
        sim.hold("88", "10-BB-511", 0);
        sim.hold("88", "10-BB-482", 1);
        for (int i = 0; i < 31; i++) sim.step();
        occupancy.bunchingTick();
        occupancy.bunchingTick(); // same episode → deduped
        assertThat(occAlerts()).containsExactly("88: iki avtobus topa yığılıb");
    }

    java.util.List<String> occAlerts() {
        return events.stream(StreamEvent.class).filter(e -> e.name().equals("alert.created") && e.role() == az.yolcu.user.Role.DISPATCHER).map(e -> (AlertDto) e.data())
                .filter(a -> a.category().equals("OCC")).map(AlertDto::title).toList();
    }

    double avg88() { return sim.buses("88").stream().mapToInt(BusPosition::occupancy).average().orElseThrow(); }
}
