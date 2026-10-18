package az.yolcu.proposal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.common.GeoUtil;
import az.yolcu.feed.dto.AlertDto;
import az.yolcu.proposal.dto.HubProposal;
import az.yolcu.security.JwtService;
import az.yolcu.stream.StreamEvent;
import az.yolcu.traffic.TrafficService;
import org.junit.jupiter.api.AfterEach;
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
class HubProposalTest {
    static final double GENCLIK_LAT = 40.400407, GENCLIK_LON = 49.851419; // metro_ridership station point

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ApplicationEvents events;
    @Autowired TrafficService traffic;
    @Autowired HubProposalRule rule;

    String auth() {
        return "Bearer " + jwt.issue(jdbc.queryForObject("select id from users where email = 'dispatcher@yolcu.az'", Long.class), "DISPATCHER");
    }

    @AfterEach
    void cleanUp() {
        traffic.reset();
        jdbc.update("delete from hub_proposals");
        jdbc.update("delete from alerts where id > 7");
    }

    @Test
    void firesOnLongJamOnly() {
        assertThat(rule.check(false)).isEmpty(); // nothing jammed for 20 min yet
        traffic.force("babek", "JAM");
        traffic.setJamMinutes("babek", 10);
        assertThat(rule.check(false)).isEmpty();

        traffic.setJamMinutes("babek", 35);
        var created = rule.check(false);
        assertThat(created).hasSize(1);
        var p = created.get(0);
        assertThat(p.name()).isEqualTo("Babək pr. – Gənclik metrosu");
        assertThat(p.jamMinutes()).isEqualTo(35);
        assertThat(p.status()).isEqualTo("NEW");
        assertThat(p.metroM()).isBetween(1, 400);
        assertThat(p.routesCount()).isGreaterThanOrEqualTo(3);
        assertThat(p.waiting()).isGreaterThanOrEqualTo(40);
        assertThat(p.confidence()).isBetween(50, 95);
        assertThat(p.items()).contains("Metroya qədər işıqlı piyada keçidi", "Hub kamerası və canlı gəliş ekranı");
        assertThat(p.impact().costAzn()).isNotBlank();
        assertThat(GeoUtil.distanceMeters(p.lat(), p.lon(), GENCLIK_LAT, GENCLIK_LON)).isBetween(300.0, 500.0);
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("proposal.created"))
                .map(e -> ((HubProposal) e.data()).id())).contains(p.id());
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("alert.created"))
                .map(e -> ((AlertDto) e.data()).title())).contains("Yeni hub təklifi: Babək pr. – Gənclik metrosu");

        assertThat(rule.check(false)).isEmpty();  // same place is proposed once
        assertThat(rule.check(true)).hasSize(1);  // demo replay
    }

    @Test
    void acceptRejectTransitions() throws Exception {
        traffic.force("babek", "JAM");
        traffic.setJamMinutes("babek", 35);
        long a = rule.check(false).get(0).id();
        long b = rule.check(true).get(0).id();

        mvc.perform(get("/api/v1/admin/hub-proposals?status=NEW").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(b))
                .andExpect(jsonPath("$[0].impact.transferMin").isNumber())
                .andExpect(jsonPath("$[0].reason").isNotEmpty());
        mvc.perform(post("/api/v1/admin/hub-proposals/" + a + "/accept").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
        mvc.perform(post("/api/v1/admin/hub-proposals/" + a + "/reject").header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        mvc.perform(post("/api/v1/admin/hub-proposals/" + b + "/reject").header("Authorization", auth()))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(get("/api/v1/admin/hub-proposals?status=ACCEPTED").header("Authorization", auth()))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/v1/admin/hub-proposals?status=DONE").header("Authorization", auth()))
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        mvc.perform(post("/api/v1/admin/hub-proposals/999999/accept").header("Authorization", auth()))
                .andExpect(status().isNotFound());
    }
}
