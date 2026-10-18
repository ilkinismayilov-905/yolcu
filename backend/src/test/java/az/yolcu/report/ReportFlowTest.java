package az.yolcu.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.report.dto.ReportListItem;
import az.yolcu.security.JwtService;
import az.yolcu.stream.StreamEvent;
import az.yolcu.user.Role;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Each test runs in a rolled-back transaction, so the seeded DB stays pristine for other test classes. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
@RecordApplicationEvents
class ReportFlowTest {
    static final String BODY = "{\"route\":\"88\",\"bort\":\"10-BB-482\",\"category\":\"Dayanacaqda saxlamadı\","
            + "\"text\":\"Elmlər dayanacağında saxlamadı\",\"hasPhoto\":true,\"lat\":40.3755,\"lon\":49.8156}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ApplicationEvents events;

    /** n-th synthetic passenger (skips the demo passenger whose balance AuthFlowTest checks). */
    long passenger(int n) {
        return jdbc.queryForObject("select id from users where role = 'PASSENGER' order by id limit 1 offset ?", Long.class, n);
    }

    String token(long userId, String role) { return "Bearer " + jwt.issue(userId, role); }

    String dispatcher() {
        return token(jdbc.queryForObject("select id from users where email = 'dispatcher@yolcu.az'", Long.class), "DISPATCHER");
    }

    int balance(long userId) { return jdbc.queryForObject("select coin_balance from users where id = ?", Integer.class, userId); }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String auth, String body) {
        return b.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    String create(String auth) throws Exception {
        var res = mvc.perform(json(post("/api/v1/me/reports"), auth, BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.code");
    }

    @Test
    void createAwardsNoCoinsAndPublishesSse() throws Exception {
        long u = passenger(1);
        int before = balance(u);
        mvc.perform(json(post("/api/v1/me/reports"), token(u, "PASSENGER"), BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(matchesPattern("A-\\d{4}")))
                .andExpect(jsonPath("$.code").value("A-2042"))
                .andExpect(jsonPath("$.status").value("NEW"))
                .andExpect(jsonPath("$.operator").value("BakuBus MMC"))
                .andExpect(jsonPath("$.place").value("Elmlər"))
                .andExpect(jsonPath("$.coinsAwarded").value(0))
                .andExpect(jsonPath("$.coinsGiven").value(0))
                .andExpect(jsonPath("$.events[0].type").value("CREATED"))
                .andExpect(jsonPath("$.passenger").doesNotExist());
        assertThat(balance(u)).isEqualTo(before);

        var sse = events.stream(StreamEvent.class).filter(e -> e.name().equals("report.created")).toList();
        assertThat(sse).extracting(StreamEvent::role).containsExactlyInAnyOrder(Role.DISPATCHER, Role.ADMIN);
        assertThat(((ReportListItem) sse.get(0).data()).code()).isEqualTo("A-2042");
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("coin.updated") && e.userId() == u)).isEmpty();

        mvc.perform(get("/api/v1/me/reports").header("Authorization", token(u, "PASSENGER")))
                .andExpect(jsonPath("$.total").value(jdbc.queryForObject("select count(*) from reports where user_id = ?", Long.class, u)))
                .andExpect(jsonPath("$.items[0].code").value("A-2042"));
        mvc.perform(get("/api/v1/me/reports/A-2041").header("Authorization", token(passenger(3), "PASSENGER")))
                .andExpect(status().isNotFound());
    }

    @Test
    void fourthReportIn10MinutesIsRateLimited() throws Exception {
        var auth = token(passenger(2), "PASSENGER");
        for (int i = 0; i < 3; i++) create(auth);
        mvc.perform(json(post("/api/v1/me/reports"), auth, BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMIT"));
    }

    @Test
    void validation() throws Exception {
        mvc.perform(json(post("/api/v1/me/reports"), token(passenger(1), "PASSENGER"), "{\"route\":\"88\",\"category\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        mvc.perform(get("/api/v1/admin/reports").header("Authorization", token(passenger(1), "PASSENGER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminListFilters() throws Exception {
        long all = jdbc.queryForObject("select count(*) from reports", Long.class);
        long fresh = jdbc.queryForObject("select count(*) from reports where status = 'NEW'", Long.class);
        mvc.perform(get("/api/v1/admin/reports?size=10").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.total").value(all))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[0].code").value("A-2041"));
        mvc.perform(get("/api/v1/admin/reports?status=NEW&size=50").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.total").value(fresh))
                .andExpect(jsonPath("$.items[*].status", everyItem(in(List.of("NEW")))));
        mvc.perform(get("/api/v1/admin/reports?status=CLOSED&size=50").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.items[*].status", everyItem(in(List.of("ANSWERED", "RESOLVED")))));
        mvc.perform(get("/api/v1/admin/reports?q=a-2041").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].code").value("A-2041"));
        mvc.perform(get("/api/v1/admin/reports?q=99-XF&operator=xf&size=50").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.total").value(jdbc.queryForObject("select count(*) from reports where bort like '99-XF%'", Long.class)))
                .andExpect(jsonPath("$.items[*].operator", everyItem(in(List.of("Xaliq Faiqoğlu MMC")))));
        mvc.perform(get("/api/v1/admin/reports?status=BAD").header("Authorization", dispatcher()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        mvc.perform(get("/api/v1/admin/reports/stats").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.late").value(4))
                .andExpect(jsonPath("$.newCount").value(fresh));
    }

    @Test
    void forwardThenResolveOnce() throws Exception {
        long u = passenger(4);
        var code = create(token(u, "PASSENGER"));
        long id = jdbc.queryForObject("select id from reports where code = ?", Long.class, code);
        int afterCreate = balance(u);

        mvc.perform(post("/api/v1/admin/reports/" + code + "/forward").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FORWARDED"))
                .andExpect(jsonPath("$.passenger").value("Gizli"))
                .andExpect(jsonPath("$.events[1].type").value("FORWARDED"))
                .andExpect(jsonPath("$.events[1].actor").value("Leyla Məmmədova"));
        assertThat(jdbc.queryForObject("select count(*) from report_events where report_id = ? and type = 'FORWARDED'", Long.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from notifications where user_id = ? and target = ?", Long.class, u, "report:" + code)).isEqualTo(1);
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("report.updated") && Long.valueOf(u).equals(e.userId()))).hasSize(1);
        assertThat(events.stream(StreamEvent.class).filter(e -> e.name().equals("notification.created"))).hasSize(1);

        // FORWARDED → REVIEW is not allowed
        mvc.perform(json(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/admin/reports/" + code + "/status"),
                        dispatcher(), "{\"status\":\"REVIEW\",\"note\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));

        var resolve = "{\"answerText\":\"Sürücü ilə izahat işi aparıldı\"}";
        mvc.perform(json(post("/api/v1/admin/reports/" + code + "/resolve"), dispatcher(), resolve))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.answerText").value("Sürücü ilə izahat işi aparıldı"))
                .andExpect(jsonPath("$.coinsGiven").value(0));
        assertThat(balance(u)).isEqualTo(afterCreate);
        assertThat(jdbc.queryForObject("select body from notifications where user_id = ? and title = 'Şikayətiniz həll edildi'", String.class, u))
                .doesNotContain("Coin");

        mvc.perform(json(post("/api/v1/admin/reports/" + code + "/resolve"), dispatcher(), resolve))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        assertThat(balance(u)).isEqualTo(afterCreate);

        mvc.perform(json(post("/api/v1/admin/reports/" + code + "/share"), dispatcher(), "{\"targets\":[\"OPERATOR\",\"YPX\"],\"hidePersonal\":true}"))
                .andExpect(jsonPath("$.ok").value(true));
        mvc.perform(get("/api/v1/admin/reports/NOPE").header("Authorization", dispatcher()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }
}
