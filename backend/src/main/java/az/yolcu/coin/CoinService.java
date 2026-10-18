package az.yolcu.coin;

import az.yolcu.coin.dto.CoinRule;
import az.yolcu.coin.dto.CoinSummary;
import az.yolcu.coin.dto.Wallet;
import az.yolcu.common.ApiException;
import az.yolcu.common.AppClock;
import az.yolcu.config.YolcuProperties;
import az.yolcu.report.ReportService;
import az.yolcu.stream.StreamEvent;
import az.yolcu.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coin rules (02_ARCHITECTURE §9). The only place where users.coin_balance changes. */
@Service
public class CoinService {
    public static final int REVIEW = 5, REPORT = 5, REPORT_VERIFIED = 15, FARE = 60;
    public static final List<Integer> SURVEY_REWARDS = List.of(5, 10, 20);
    /** Not earned: seed opening balances and demo top-ups (excluded from wallet history / "earned today"). */
    static final List<String> NOT_EARNED = List.of("SEED", "DEMO");
    static final int HISTORY_LIMIT = 50;
    // shortcut: pilot-wide totals (design/web_dashboard.html #p-coins) are not in the demo DB; ledger sums are added on top
    static final int BASE_EARNED_TODAY = 48_210, BASE_EARNED_TODAY_USERS = 9_120, BASE_IN_CIRCULATION = 3_100_000;
    static final BigDecimal BASE_CONVERTED_MONTH = new BigDecimal("12400.00");

    private final CoinTxRepository txs;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final AppClock clock;
    private final JdbcTemplate jdbc;
    private final int rate;

    public CoinService(CoinTxRepository txs, UserRepository users, ApplicationEventPublisher events, AppClock clock,
                       JdbcTemplate jdbc, YolcuProperties props) {
        this.txs = txs;
        this.users = users;
        this.events = events;
        this.clock = clock;
        this.jdbc = jdbc;
        this.rate = props.coinRate();
    }

    /** Single source for the admin rules table and the wallet's "how to earn" list. */
    public List<CoinRule> rules() {
        int min = Collections.min(SURVEY_REWARDS), max = Collections.max(SURVEY_REWARDS);
        return List.of(
                new CoinRule("REVIEW", "Səfərdən sonra rəy", "+" + REVIEW, "Hər səfər üçün 1 dəfə"),
                new CoinRule("SURVEY", "Sorğuya cavab", "+" + min + "–" + max, "Sorğu başına 1 dəfə"),
                new CoinRule("REPORT", "Şikayət göndərmək", "+" + REPORT,
                        ReportService.RATE_WINDOW.toMinutes() + " dəqiqədə ən çox " + ReportService.RATE_LIMIT),
                new CoinRule("REPORT_VERIFIED", "Şikayət təsdiqləndi", "+" + REPORT_VERIFIED, "Operator və ya AYNA təsdiqi ilə"),
                new CoinRule("FARE", "Gediş haqqını Yolçu Coin ilə ödəmək", "−" + FARE, manat(FARE).replace('.', ',') + " ₼ gediş"));
    }

    /** Adds a ledger row and updates the balance; returns the awarded amount, 0 if (user, reason, refId) was already awarded. */
    @Transactional
    public int award(long userId, int amount, String reason, String refId, String label) {
        if (amount < 0) throw new IllegalArgumentException("award amount < 0, use spend");
        // unique(user_id, reason, ref_id) in coin_tx is the backstop for concurrent duplicates
        if (txs.existsByUserIdAndReasonAndRefId(userId, reason, refId)) return 0;
        txs.save(new CoinTx(userId, amount, reason, refId, label, clock.now()));
        users.addCoins(userId, amount);
        publishBalance(userId);
        return amount;
    }

    /** Takes coins; 409 COINS_INSUFFICIENT if the balance is lower; returns 0 if (user, reason, refId) was already spent. */
    @Transactional
    public int spend(long userId, int amount, String reason, String refId, String label) {
        if (amount <= 0) throw new IllegalArgumentException("spend amount must be > 0");
        if (txs.existsByUserIdAndReasonAndRefId(userId, reason, refId)) return 0;
        // conditional update: two concurrent spends cannot both pass the balance check
        if (users.spendCoins(userId, amount) == 0) {
            throw ApiException.coinsInsufficient("Yolçu Coin kifayət etmir: " + users.coinBalance(userId) + " var, " + amount + " lazımdır");
        }
        txs.save(new CoinTx(userId, -amount, reason, refId, label, clock.now()));
        publishBalance(userId);
        return amount;
    }

    @Transactional(readOnly = true)
    public int sumFor(long userId, Collection<String> refIds) {
        return txs.sumByUserIdAndRefIdIn(userId, refIds);
    }

    @Transactional(readOnly = true)
    public Wallet wallet(long userId) {
        int balance = users.coinBalance(userId);
        var history = jdbc.query("""
                        select t.label, t.amount, t.created_at, c.status from coin_tx t
                        left join coin_conversions c on t.reason = 'CONVERSION' and t.ref_id = concat('conv:', c.id)
                        where t.user_id = ? and t.reason not in ('SEED', 'DEMO') order by t.created_at desc, t.id desc limit ?""",
                (rs, i) -> new Wallet.Tx(rs.getString(1), rs.getInt(2),
                        AppClock.local(rs.getObject(3, OffsetDateTime.class)), rs.getString(4)),
                userId, HISTORY_LIMIT);
        Integer earned = jdbc.queryForObject("select coalesce(sum(amount), 0) from coin_tx where user_id = ? and amount > 0 "
                + "and reason not in ('SEED', 'DEMO') and created_at >= ?", Integer.class, userId, today());
        var earning = rules().stream().filter(r -> r.amount().startsWith("+")).map(r -> new Wallet.Rule(r.action(), r.amount())).toList();
        return new Wallet(balance, manat(balance), rate, earned, history, earning);
    }

    @Transactional(readOnly = true)
    public CoinSummary summary() {
        var today = today();
        var earned = jdbc.queryForMap("select coalesce(sum(amount), 0) as s, count(distinct user_id) as u from coin_tx "
                + "where amount > 0 and reason not in ('SEED', 'DEMO') and created_at >= ?", today);
        Long circulation = jdbc.queryForObject("select coalesce(sum(coin_balance), 0) from users where role = 'PASSENGER'", Long.class);
        var month = today.with(TemporalAdjusters.firstDayOfMonth());
        BigDecimal converted = jdbc.queryForObject("select coalesce(sum(azn), 0) from coin_conversions "
                + "where status = 'COMPLETED' and completed_at >= ?", BigDecimal.class, month);
        return new CoinSummary(BASE_EARNED_TODAY + ((Number) earned.get("s")).intValue(),
                BASE_EARNED_TODAY_USERS + ((Number) earned.get("u")).intValue(),
                BASE_IN_CIRCULATION + circulation,
                BASE_CONVERTED_MONTH.add(converted).setScale(2, RoundingMode.HALF_UP).toPlainString(), rate);
    }

    /** coins → AZN at yolcu.coin-rate, "2.45". */
    public String manat(int coins) {
        return BigDecimal.valueOf(coins).divide(BigDecimal.valueOf(rate), 2, RoundingMode.HALF_UP).toPlainString();
    }

    /** Start of the demo clock's day in Baku. */
    OffsetDateTime today() {
        return clock.now().atZoneSameInstant(AppClock.ZONE).truncatedTo(ChronoUnit.DAYS).toOffsetDateTime();
    }

    private void publishBalance(long userId) {
        events.publishEvent(StreamEvent.toUser(userId, "coin.updated", Map.of("balance", users.coinBalance(userId))));
    }
}
