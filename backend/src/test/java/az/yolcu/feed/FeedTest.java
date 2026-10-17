package az.yolcu.feed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.feed.dto.AlertDto;
import az.yolcu.security.JwtService;
import az.yolcu.sim.SimulationService;
import az.yolcu.stream.StreamEvent;
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
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
@RecordApplicationEvents
class FeedTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ApplicationEvents events;
    @Autowired SimulationService sim;

    String dispatcher() {
        return "Bearer " + jwt.issue(jdbc.queryForObject("select id from users where email = 'dispatcher@yolcu.az'", Long.class), "DISPATCHER");
    }

    @Test
    void seededFeedNewestFirst() throws Exception {
        mvc.perform(get("/api/v1/admin/feed?limit=50").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].category").value("HUB"))
                .andExpect(jsonPath("$[0].target").value("p-hubs"))
                .andExpect(jsonPath("$[6].category").value("WEATHER"));
    }

    @Test
    void reportAddsReportAlert() throws Exception {
        long u = jdbc.queryForObject("select id from users where role = 'PASSENGER' order by id limit 1 offset 3", Long.class);
        mvc.perform(post("/api/v1/me/reports").header("Authorization", "Bearer " + jwt.issue(u, "PASSENGER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"route\":\"4\",\"category\":\"Çirkli salon\",\"text\":\"Salon çirklidir\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/admin/feed").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].category").value("REPORT"))
                .andExpect(jsonPath("$[0].body").value("Salon çirklidir"));
        var sse = events.stream(StreamEvent.class).filter(e -> e.name().equals("alert.created")).toList();
        assertThat(sse).hasSize(2); // DISPATCHER + ADMIN
        assertThat(((AlertDto) sse.get(0).data()).category()).isEqualTo("REPORT");
    }

    @Test
    void kpisFromSimulator() throws Exception {
        sim.step();
        mvc.perform(get("/api/v1/admin/kpis").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avgSpeedKmh").value(20)) // fleet-weighted route speeds
                .andExpect(jsonPath("$.waitingAtHubs").isNumber())
                .andExpect(jsonPath("$.avgOccupancy").isNumber())
                .andExpect(jsonPath("$.satisfaction").isNumber());
    }
}
