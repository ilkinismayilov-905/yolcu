package az.yolcu.hub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.common.AppClock;
import az.yolcu.feed.dto.AlertDto;
import az.yolcu.hub.dto.AdminHub;
import az.yolcu.biletim.dto.BiletimArrival;
import az.yolcu.security.JwtService;
import az.yolcu.sim.SimulationService;
import az.yolcu.stream.StreamEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@RecordApplicationEvents
class HubBiletimTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ApplicationEvents events;
    @Autowired SimulationService sim;
    @Autowired ObjectMapper mapper;
    @Autowired AppClock clock;

    String auth(String email, String role) {
        return "Bearer " + jwt.issue(jdbc.queryForObject("select id from users where email = ?", Long.class, email), role);
    }

    String dispatcher() { return auth("dispatcher@yolcu.az", "DISPATCHER"); }

    <T> T getJson(String url, TypeReference<T> type) throws Exception {
        var body = mvc.perform(get(url).header("Authorization", dispatcher())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return mapper.readValue(body, type);
    }

    @BeforeEach
    void warmUp() { for (int i = 0; i < 20; i++) sim.step(); }

    @AfterEach
    void restore() throws Exception {
        mvc.perform(post("/api/v1/demo/reset").header("Authorization", dispatcher())).andExpect(status().isOk());
        jdbc.update("delete from alerts where id > 7");
    }

    @Test
    void hubsShapeAndLevels() throws Exception {
        mvc.perform(get("/api/v1/admin/hubs").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isString())
                .andExpect(jsonPath("$[0].lat").isNumber())
                .andExpect(jsonPath("$[0].trend10").isNumber())
                .andExpect(jsonPath("$[0].forecast15").isNumber())
                .andExpect(jsonPath("$[0].avgWaitMin").isNumber())
                .andExpect(jsonPath("$[0].camera").isString())
                .andExpect(jsonPath("$[0].kind").isString());
        var hubs = getJson("/api/v1/admin/hubs", new TypeReference<List<AdminHub>>() {});
        assertThat(hubs).hasSize(8);
        for (var h : hubs) {
            double r = (double) h.count() / h.capacity();
            assertThat(h.level()).isEqualTo(r >= 0.8 ? "HIGH" : r >= 0.55 ? "MEDIUM" : "LOW");
            assertThat(h.avgWaitMin()).isEqualTo((int) Math.min(20, Math.round(2 + 10 * r)));
            if (h.level().equals("LOW")) assertThat(h.recommendation()).isNull();
            assertThat(h.camera()).startsWith("K-");
        }
        assertThat(hubs).filteredOn(h -> h.kind().equals("HUB")).extracting(AdminHub::id)
                .containsExactlyInAnyOrder("may28", "koroglu", "avtovagzal");
        var may28 = hubs.stream().filter(h -> h.id().equals("may28")).findFirst().orElseThrow();
        assertThat(may28.camera()).isEqualTo("K-201");
        if (!may28.level().equals("LOW")) assertThat(may28.recommendation()).isNotBlank();
    }

    @Test
    void applyWritesAiAlertAndUnknownIs404() throws Exception {
        jdbc.update("update route_config set interval_min = 16 where route = '88'"); // 88 at 28 May surely wants a tighter interval
        sim.step();
        var may28 = getJson("/api/v1/admin/hubs", new TypeReference<List<AdminHub>>() {}).stream()
                .filter(h -> h.id().equals("may28")).findFirst().orElseThrow();
        assertThat(may28.level()).isNotEqualTo("LOW");
        assertThat(may28.recommendation()).matches("\\S+ intervalı \\d+ → \\d+ dəq");
        events.clear();
        mvc.perform(post("/api/v1/admin/hubs/may28/apply").header("Authorization", dispatcher()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        var ai = events.stream(StreamEvent.class).filter(e -> e.name().equals("alert.created")).map(e -> (AlertDto) e.data())
                .filter(a -> a.category().equals("AI")).toList();
        assertThat(ai).anyMatch(a -> a.title().equals("28 May: tövsiyə tətbiq olundu") && a.body().equals(may28.recommendation())
                && a.target().equals("p-hubs"));
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("route.updated")).count()).isEqualTo(1);
        mvc.perform(post("/api/v1/admin/hubs/nope/apply").header("Authorization", dispatcher()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void arrivalsSortedAndGanjaArrivalFirstUntilReset() throws Exception {
        var list = getJson("/api/v1/admin/biletim/arrivals", new TypeReference<List<BiletimArrival>>() {});
        assertThat(list).hasSize(7);
        assertThat(list).allMatch(a -> a.time().matches("\\d\\d:\\d\\d") && a.nextCityBusMin() >= 1
                && a.level().equals(a.ticketsSold() >= 35 ? "HIGH" : a.ticketsSold() >= 20 ? "MEDIUM" : "LOW"));

        mvc.perform(post("/api/v1/demo/trigger/ganja-arrival").header("Authorization", dispatcher())).andExpect(status().isOk());
        var after = getJson("/api/v1/admin/biletim/arrivals", new TypeReference<List<BiletimArrival>>() {});
        assertThat(after).hasSize(8);
        var g = after.get(0);
        assertThat(g.from()).isEqualTo("Gəncə");
        assertThat(g.ticketsSold()).isEqualTo(45);
        assertThat(g.level()).isEqualTo("HIGH");
        var now = clock.now().atZoneSameInstant(AppClock.ZONE).toLocalTime();
        long min = Duration.between(now.withSecond(0).withNano(0), LocalTime.parse(g.time())).toMinutes();
        assertThat(min).isBetween(4L, 5L);
        assertThat(after.subList(1, 8)).isEqualTo(list.subList(0, 7));

        mvc.perform(post("/api/v1/demo/reset").header("Authorization", dispatcher())).andExpect(status().isOk());
        assertThat(getJson("/api/v1/admin/biletim/arrivals", new TypeReference<List<BiletimArrival>>() {})).hasSize(7);
    }

    @Test
    void passengerIsForbidden() throws Exception {
        var p = "Bearer " + jwt.issue(jdbc.queryForObject("select min(id) from users where role = 'PASSENGER'", Long.class), "PASSENGER");
        for (var url : List.of("/api/v1/admin/hubs", "/api/v1/admin/biletim/arrivals")) {
            mvc.perform(get(url).header("Authorization", p)).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        mvc.perform(post("/api/v1/admin/hubs/may28/apply").header("Authorization", p)).andExpect(status().isForbidden());
    }
}
