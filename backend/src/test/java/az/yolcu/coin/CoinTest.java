package az.yolcu.coin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.coin.dto.ConversionRow;
import az.yolcu.common.ApiException;
import az.yolcu.feed.dto.AlertDto;
import az.yolcu.notification.dto.NotificationDto;
import az.yolcu.security.JwtService;
import az.yolcu.stream.StreamEvent;
import az.yolcu.user.Role;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.web.servlet.ResultActions;

/** BE-5.1 / 5.3 / 5.5. The bank job is driven by passing the wall-clock time to completeDue, no sleeping. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@RecordApplicationEvents
class CoinTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired ApplicationEvents events;
    @Autowired CoinService coins;
    @Autowired ConversionService conversions;

    long nigar;

    String dispatcher() {
        return "Bearer " + jwt.issue(jdbc.queryForObject("select id from users where email = 'dispatcher@yolcu.az'", Long.class), "DISPATCHER");
    }

    String passenger() { return "Bearer " + jwt.issue(nigar, "PASSENGER"); }

    int balance(long id) { return jdbc.queryForObject("select coin_balance from users where id = ?", Integer.class, id); }

    ResultActions convert(int amount) throws Exception {
        return mvc.perform(post("/api/v1/me/wallet/conversions").header("Authorization", passenger())
                .contentType(MediaType.APPLICATION_JSON).content("{\"coins\":" + amount + ",\"cardMask\":\"•• 0193\"}"));
    }

    List<StreamEvent> stream(String name) { return events.stream(StreamEvent.class).filter(e -> e.name().equals(name)).toList(); }

    @BeforeEach
    void setUp() throws Exception {
        nigar = jdbc.queryForObject("select min(id) from users where role = 'PASSENGER'", Long.class);
        mvc.perform(post("/api/v1/demo/reset").header("Authorization", dispatcher())).andExpect(status().isOk());
        events.clear();
    }

    @AfterEach
    void tearDown() throws Exception {
        mvc.perform(post("/api/v1/demo/reset").header("Authorization", dispatcher()));
        jdbc.update("delete from alerts where category = 'COIN'");
    }

    @Test
    void spendBelowZeroIsRejected() throws Exception {
        convert(300).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COINS_INSUFFICIENT"))
                .andExpect(jsonPath("$.error.message").value("Yolçu Coin kifayət etmir: 245 var, 300 lazımdır"));
        assertThat(balance(nigar)).isEqualTo(245);
        assertThat(jdbc.queryForObject("select count(*) from coin_conversions where user_id = ?", Long.class, nigar)).isZero();
        assertThatThrownBy(() -> coins.spend(nigar, 246, "FARE", "t-x", "x"))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("COINS_INSUFFICIENT"));
    }

    @Test
    void awardAndSpendAreIdempotent() {
        assertThat(coins.award(nigar, 5, "REVIEW", "t-1", "Səfər rəyi")).isEqualTo(5);
        assertThat(coins.award(nigar, 5, "REVIEW", "t-1", "Səfər rəyi")).isZero();
        assertThat(coins.spend(nigar, 60, "FARE", "t-1", "Gediş")).isEqualTo(60);
        assertThat(coins.spend(nigar, 60, "FARE", "t-1", "Gediş")).isZero();
        assertThat(balance(nigar)).isEqualTo(190);
        assertThat(stream("coin.updated")).extracting(e -> (Object) ((Map<?, ?>) e.data()).get("balance")).containsExactly(250, 190);
    }

    @Test
    void conversionValidationAndBalance() throws Exception {
        convert(99).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        convert(150).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION"));
        convert(100).andExpect(status().isCreated())
                .andExpect(jsonPath("$.coins").value(100))
                .andExpect(jsonPath("$.azn").value("1.00"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.coinBalance").value(145));
        convert(100).andExpect(status().isCreated()).andExpect(jsonPath("$.coinBalance").value(45));
        assertThat(balance(nigar)).isEqualTo(45);
        assertThat(jdbc.queryForObject("select sum(amount) from coin_tx where user_id = ?", Integer.class, nigar)).isEqualTo(45);
        assertThat(stream("conversion.updated")).hasSize(4); // DISPATCHER + ADMIN per conversion
    }

    @Test
    void bankJobCompletesAfterDelay() throws Exception {
        convert(200).andExpect(status().isCreated()).andExpect(jsonPath("$.coinBalance").value(45));
        long id = jdbc.queryForObject("select max(id) from coin_conversions where user_id = ?", Long.class, nigar);
        events.clear();
        assertThat(conversions.completeDue(OffsetDateTime.now())).isZero();
        assertThat(conversions.completeDue(OffsetDateTime.now().plusSeconds(61))).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from coin_conversions where id = ?", String.class, id)).isEqualTo("COMPLETED");

        var n = (NotificationDto) stream("notification.created").get(0).data();
        assertThat(n.type()).isEqualTo("COIN");
        assertThat(n.target()).isEqualTo("wallet");
        assertThat(n.body()).isEqualTo("200 Yolçu Coin → 2,00 ₼ kartınıza köçürüldü (•• 0193)");
        assertThat(stream("alert.created")).extracting(e -> ((AlertDto) e.data()).category()).contains("COIN");
        var row = (ConversionRow) stream("conversion.updated").stream().filter(e -> e.role() == Role.DISPATCHER).findFirst().orElseThrow().data();
        assertThat(row.id()).isEqualTo(id);
        assertThat(row.status()).isEqualTo("COMPLETED");
        assertThat(row.completedAt()).isNotNull();
        // seeded PENDING rows are not handled by the bank simulation
        assertThat(jdbc.queryForObject("select count(*) from coin_conversions where status = 'PENDING'", Long.class)).isEqualTo(2);
        assertThat(conversions.completeDue(OffsetDateTime.now().plusSeconds(61))).isZero();
    }

    @Test
    void walletHidesSeedRows() throws Exception {
        jdbc.update("insert into coin_tx (user_id, amount, reason, ref_id, label, created_at) values (?, 100, 'SEED', 't-seed', 'Başlanğıc balansı', ?)",
                nigar, coins.today().plusMinutes(1));
        coins.award(nigar, 5, "REVIEW", "t-2", "Səfər rəyi, 88");
        convert(100).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/me/wallet").header("Authorization", passenger()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(150))
                .andExpect(jsonPath("$.aznEquivalent").value("1.50"))
                .andExpect(jsonPath("$.rate").value(100))
                .andExpect(jsonPath("$.earnedToday").value(5))
                .andExpect(jsonPath("$.history[0].label").value("Banka çevirmə: 1,00 ₼"))
                .andExpect(jsonPath("$.history[0].amount").value(-100))
                .andExpect(jsonPath("$.history[0].status").value("PENDING"))
                .andExpect(jsonPath("$.history[?(@.label == 'Başlanğıc balansı')]").isEmpty())
                .andExpect(jsonPath("$.rules.length()").value(4))
                .andExpect(jsonPath("$.rules[1].amount").value("+5–20"));
    }

    @Test
    void adminSummaryConversionsRules() throws Exception {
        mvc.perform(get("/api/v1/admin/coins/summary").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.earnedToday").value(greaterThanOrEqualTo(48210)))
                .andExpect(jsonPath("$.inCirculation").value(greaterThanOrEqualTo(3_100_000)))
                .andExpect(jsonPath("$.convertedThisMonthAzn").isString())
                .andExpect(jsonPath("$.rate").value(100));
        mvc.perform(get("/api/v1/admin/coins/conversions?page=0&size=10").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items[0].coins").value(500))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].azn").value("5.00"))
                .andExpect(jsonPath("$.items[0].userName").value(org.hamcrest.Matchers.matchesPattern("\\S+ \\S\\.")))
                .andExpect(jsonPath("$.items[0].phoneMask").value(org.hamcrest.Matchers.matchesPattern("\\+994 \\d\\d ••• \\d\\d")))
                .andExpect(jsonPath("$.items[1].status").value("PENDING"))
                .andExpect(jsonPath("$.items[?(@.status == 'COMPLETED')].id", org.hamcrest.Matchers.hasSize(3)));
        mvc.perform(get("/api/v1/admin/coins/rules").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].action").value("Səfərdən sonra rəy"))
                .andExpect(jsonPath("$[2].limit").value("10 dəqiqədə ən çox 3"))
                .andExpect(jsonPath("$[4].amount").value("−60"))
                .andExpect(jsonPath("$[4].limit").value("0,60 ₼ gediş"));
        mvc.perform(get("/api/v1/admin/coins/summary").header("Authorization", passenger())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/coins/conversions").header("Authorization", passenger())).andExpect(status().isForbidden());
    }

    @Test
    void demoTriggerAndReset() throws Exception {
        mvc.perform(post("/api/v1/demo/trigger/coin-conversion").header("Authorization", dispatcher()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        long rauf = conversions.demoUserId();
        assertThat(jdbc.queryForObject("select status from coin_conversions where user_id = ? order by id desc limit 1", String.class, rauf))
                .isEqualTo("PENDING");
        assertThat(balance(rauf)).isZero();
        assertThat(jdbc.queryForObject("select sum(amount) from coin_tx where user_id = ?", Integer.class, rauf)).isZero();
        assertThat(conversions.completeDue(OffsetDateTime.now().plusSeconds(61))).isEqualTo(1);

        mvc.perform(post("/api/v1/demo/reset").header("Authorization", dispatcher())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from coin_conversions where user_id = ?", Long.class, rauf)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from coin_conversions", Long.class)).isEqualTo(5);
    }
}
