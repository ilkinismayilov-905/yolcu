package az.yolcu.survey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.notification.dto.NotificationDto;
import az.yolcu.security.JwtService;
import az.yolcu.stream.StreamEvent;
import az.yolcu.survey.dto.SurveyListItem;
import az.yolcu.user.Role;
import com.jayway.jsonpath.JsonPath;
import java.util.Arrays;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
@RecordApplicationEvents
class SurveyFlowTest {
    static final String NEW = "{\"title\":\"Gecə avtobusları\",\"audience\":\"ALL\",\"coinReward\":10,"
            + "\"questions\":[{\"text\":\"Lazımdırmı?\",\"options\":[\"Bəli\",\"Xeyr\"]}],\"publish\":%s}";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ApplicationEvents events;

    String token(long userId, String role) { return "Bearer " + jwt.issue(userId, role); }

    String dispatcher() {
        return token(jdbc.queryForObject("select id from users where email = 'dispatcher@yolcu.az'", Long.class), "DISPATCHER");
    }

    long surveyId(String status, int n) {
        return jdbc.queryForObject("select id from surveys where status = ? order by id limit 1 offset ?", Long.class, status, n);
    }

    long passenger(int n) {
        return jdbc.queryForObject("select id from users where role = 'PASSENGER' order by id limit 1 offset ?", Long.class, n);
    }

    long passengers() { return jdbc.queryForObject("select count(*) from users where role = 'PASSENGER'", Long.class); }

    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String auth, String body) {
        return b.header("Authorization", auth).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    long create(boolean publish) throws Exception {
        var res = mvc.perform(json(post("/api/v1/admin/surveys"), dispatcher(), NEW.formatted(publish)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(res, "$.id")).longValue();
    }

    List<StreamEvent> sse(String name) { return events.stream(StreamEvent.class).filter(e -> e.name().equals(name)).toList(); }

    @Test
    void listNewestFirstWithStatusFilter() throws Exception {
        mvc.perform(get("/api/v1/admin/surveys").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.items[0].title").value("Avtovağzal ətrafında yeni ekspress xətt"))
                .andExpect(jsonPath("$.items[0].responses").value(3120 + 20))
                .andExpect(jsonPath("$.items[0].audienceLabel").value("Bütün istifadəçilər"))
                .andExpect(jsonPath("$.items[0].coinReward").value(10))
                .andExpect(jsonPath("$.items[5].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[5].responses").value(0))
                .andExpect(jsonPath("$.items[5].publishedAt").isEmpty());
        mvc.perform(get("/api/v1/admin/surveys?status=ACTIVE").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/v1/admin/surveys?status=CLOSED&size=2").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/v1/admin/surveys?status=NOPE").header("Authorization", dispatcher()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    @Test
    void resultsAddRealAnswersToSeedAndPercentsSumTo100() throws Exception {
        var res = mvc.perform(get("/api/v1/admin/surveys/" + surveyId("ACTIVE", 0) + "/results").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responses").value(3140))
                .andExpect(jsonPath("$.questions.length()").value(3))
                .andExpect(jsonPath("$.questions[0].options[0].label").value("Hər gün"))
                .andReturn().getResponse().getContentAsString();
        for (int q = 0; q < 3; q++) {
            List<Integer> pct = JsonPath.read(res, "$.questions[" + q + "].options[*].percent");
            List<Integer> counts = JsonPath.read(res, "$.questions[" + q + "].options[*].count");
            assertThat(pct.stream().mapToInt(i -> i).sum()).isEqualTo(100);
            assertThat(counts.stream().mapToInt(i -> i).sum()).isEqualTo((Integer) JsonPath.read(res, "$.questions[" + q + "].total"));
        }
    }

    @Test
    void percentsUseLargestRemainder() {
        assertThat(SurveyService.percents(new long[]{1, 1, 1})).containsExactly(34, 33, 33);
        assertThat(SurveyService.percents(new long[]{2, 1})).containsExactly(67, 33);
        assertThat(SurveyService.percents(new long[]{0, 0})).containsExactly(0, 0);
        assertThat(Arrays.stream(SurveyService.percents(new long[]{686, 1154, 905, 375})).sum()).isEqualTo(100);
    }

    @Test
    void createPublishedNotifiesWholeAudience() throws Exception {
        long id = create(true);
        long n = jdbc.queryForObject("select count(*) from notifications where target = ?", Long.class, "survey:" + id);
        assertThat(n).isEqualTo(passengers()).isGreaterThan(0);
        var sse = sse("notification.created");
        assertThat(sse).hasSize((int) n);
        var dto = (NotificationDto) sse.get(0).data();
        assertThat(dto.target()).isEqualTo("survey:" + id);
        assertThat(dto.type()).isEqualTo("SURVEY");
        assertThat(dto.body()).isEqualTo("1 dəqiqəlik sorğu. Cavab verin, 10 Yolçu Coin qazanın.");

        mvc.perform(get("/api/v1/me/notifications?size=5").header("Authorization", token(passenger(0), "PASSENGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].target").value("survey:" + id))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.size").value(5));
    }

    @Test
    void routeAudienceOnlyReachesItsPassengers() throws Exception {
        var body = NEW.formatted(true).replace("\"ALL\"", "\"ROUTE_88_4\"");
        var res = mvc.perform(json(post("/api/v1/admin/surveys"), dispatcher(), body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(res, "$.id")).longValue();
        long expected = jdbc.queryForObject("select count(distinct user_id) from trips where route in ('88','4')", Long.class);
        assertThat(jdbc.queryForObject("select count(*) from notifications where target = ?", Long.class, "survey:" + id))
                .isEqualTo(expected).isGreaterThan(0);
    }

    @Test
    void draftPublishCloseAndInvalidTransitions() throws Exception {
        long id = create(false);
        assertThat(jdbc.queryForObject("select count(*) from notifications where target = ?", Long.class, "survey:" + id)).isZero();
        mvc.perform(post("/api/v1/admin/surveys/" + id + "/close").header("Authorization", dispatcher()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        mvc.perform(post("/api/v1/admin/surveys/" + id + "/publish").header("Authorization", dispatcher()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());
        assertThat(jdbc.queryForObject("select count(*) from notifications where target = ?", Long.class, "survey:" + id))
                .isEqualTo(passengers());
        mvc.perform(post("/api/v1/admin/surveys/" + id + "/publish").header("Authorization", dispatcher()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/surveys/" + id + "/close").header("Authorization", dispatcher()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());
        mvc.perform(post("/api/v1/admin/surveys/" + id + "/close").header("Authorization", dispatcher()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/surveys/999999/publish").header("Authorization", dispatcher()))
                .andExpect(status().isNotFound());
    }

    @Test
    void createValidation() throws Exception {
        mvc.perform(json(post("/api/v1/admin/surveys"), dispatcher(), NEW.formatted(false).replace(":10", ":7")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        mvc.perform(json(post("/api/v1/admin/surveys"), dispatcher(), NEW.formatted(false).replace("[\"Bəli\",\"Xeyr\"]", "[\"Bəli\"]")))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/admin/surveys"), dispatcher(), NEW.formatted(false).replace("\"ALL\"", "\"MARS\"")))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/admin/surveys"), dispatcher(), NEW.formatted(false).replace("Gecə avtobusları", " ")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void answerOnceAwardsCoinsAndPushesSurveyUpdated() throws Exception {
        long id = create(true);
        long u = passenger(2);
        var auth = token(u, "PASSENGER");
        int before = jdbc.queryForObject("select coin_balance from users where id = ?", Integer.class, u);
        var get = mvc.perform(get("/api/v1/me/surveys/" + id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(false))
                .andExpect(jsonPath("$.coinReward").value(10))
                .andExpect(jsonPath("$.questions[0].options[1]").value("Xeyr"))
                .andReturn().getResponse().getContentAsString();
        long qid = ((Number) JsonPath.read(get, "$.questions[0].id")).longValue();
        var body = "{\"answers\":{\"" + qid + "\":\"Bəli\"}}";

        mvc.perform(json(post("/api/v1/me/surveys/" + id + "/answers"), auth, "{\"answers\":{\"" + qid + "\":\"Bəlkə\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/me/surveys/" + id + "/answers"), auth, body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coinsAwarded").value(10))
                .andExpect(jsonPath("$.coinBalance").value(before + 10));
        mvc.perform(json(post("/api/v1/me/surveys/" + id + "/answers"), auth, body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ALREADY_DONE"));
        mvc.perform(get("/api/v1/me/surveys/" + id).header("Authorization", auth)).andExpect(jsonPath("$.answered").value(true));

        var updated = sse("survey.updated");
        var last = (SurveyListItem) updated.get(updated.size() - 1).data();
        assertThat(last.responses()).isEqualTo(1);
        assertThat(updated.stream().filter(e -> ((SurveyListItem) e.data()).responses() == 1))
                .extracting(StreamEvent::role).containsExactlyInAnyOrder(Role.DISPATCHER, Role.ADMIN);
        assertThat(sse("coin.updated")).anyMatch(e -> e.userId() == u);
        mvc.perform(get("/api/v1/admin/surveys/" + id + "/results").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.responses").value(1))
                .andExpect(jsonPath("$.questions[0].options[0].count").value(1))
                .andExpect(jsonPath("$.questions[0].options[0].percent").value(100));
    }

    @Test
    void closedAndDraftSurveysCannotBeAnswered() throws Exception {
        var auth = token(passenger(3), "PASSENGER");
        mvc.perform(get("/api/v1/me/surveys/" + surveyId("DRAFT", 0)).header("Authorization", auth)).andExpect(status().isNotFound());
        long closed = create(true);
        mvc.perform(post("/api/v1/admin/surveys/" + closed + "/close").header("Authorization", dispatcher()));
        long qid = jdbc.queryForObject("select id from survey_questions where survey_id = ?", Long.class, closed);
        mvc.perform(json(post("/api/v1/me/surveys/" + closed + "/answers"), auth, "{\"answers\":{\"" + qid + "\":\"Bəli\"}}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    @Test
    void simulateAddsAnswersAndPublishesOnce() throws Exception {
        long id = surveyId("ACTIVE", 0);
        long synthetic = passengers();
        mvc.perform(post("/api/v1/demo/surveys/" + id + "/simulate-answers").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(50))
                .andExpect(jsonPath("$.survey.responses").value(3140 + 50));
        assertThat(sse("survey.updated")).hasSize(2); // DISPATCHER + ADMIN, one batch
        assertThat(sse("coin.updated")).isEmpty();
        assertThat(passengers()).isEqualTo(synthetic + 50);
        // same synthetic users are reused for a different survey
        mvc.perform(post("/api/v1/demo/surveys/" + surveyId("ACTIVE", 1) + "/simulate-answers?count=30").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.created").value(30));
        assertThat(passengers()).isEqualTo(synthetic + 50);
        mvc.perform(get("/api/v1/admin/surveys/" + id + "/results").header("Authorization", dispatcher()))
                .andExpect(jsonPath("$.responses").value(3190));
        // synthetic passengers are not part of any audience
        long published = create(true);
        assertThat(jdbc.queryForObject("select count(*) from notifications where target = ?", Long.class, "survey:" + published))
                .isEqualTo(synthetic);
        mvc.perform(post("/api/v1/demo/surveys/" + id + "/simulate-answers?count=501").header("Authorization", dispatcher()))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/demo/surveys/" + surveyId("DRAFT", 0) + "/simulate-answers").header("Authorization", dispatcher()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void passengerCannotUseAdminOrDemo() throws Exception {
        var auth = token(passenger(0), "PASSENGER");
        mvc.perform(get("/api/v1/admin/surveys").header("Authorization", auth))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(json(post("/api/v1/admin/surveys"), auth, NEW.formatted(true))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/demo/surveys/1/simulate-answers").header("Authorization", auth)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/surveys")).andExpect(status().isUnauthorized());
    }
}
