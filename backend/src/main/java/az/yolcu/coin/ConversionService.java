package az.yolcu.coin;

import az.yolcu.coin.dto.ConversionCreated;
import az.yolcu.coin.dto.ConversionRequest;
import az.yolcu.coin.dto.ConversionRow;
import az.yolcu.common.ApiException;
import az.yolcu.common.AppClock;
import az.yolcu.common.PageResponse;
import az.yolcu.config.YolcuProperties;
import az.yolcu.feed.FeedService;
import az.yolcu.notification.NotificationService;
import az.yolcu.stream.StreamEvent;
import az.yolcu.user.Role;
import az.yolcu.user.UserRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BE-5.3 / BE-5.5: coin → AZN conversions (request REAL, bank answer simulated by BankSimJob). */
@Service
public class ConversionService {
    /** Minimum = 1 ₼ at the 500 rate; amounts must be a multiple of STEP. */
    public static final int MIN_COINS = 500, STEP = 100;
    public static final String DEFAULT_CARD = "•• 0193";
    /**
     * Demo passenger for POST /demo/trigger/coin-conversion (created on first use). users.synthetic stays false on purpose:
     * synthetic users are the survey simulator's answer pool, this one must not be consumed there.
     */
    public static final String DEMO_PHONE = "+994 51 200 00 92", DEMO_NAME = "Rauf Həsənli", DEMO_CARD = "•• 1205";
    public static final int DEMO_COINS = 1000;
    private static final Logger log = LoggerFactory.getLogger(ConversionService.class);
    private static final String SELECT = "select c.id, c.coins, c.azn, c.card_mask, c.status, c.created_at, c.completed_at, "
            + "u.full_name, u.phone from coin_conversions c join users u on u.id = c.user_id ";

    private final CoinService coins;
    private final NotificationService notifications;
    private final FeedService feed;
    private final UserRepository users;
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final AppClock clock;
    private final int bankDelaySeconds;

    public ConversionService(CoinService coins, NotificationService notifications, FeedService feed, UserRepository users,
                             JdbcTemplate jdbc, ApplicationEventPublisher events, AppClock clock, YolcuProperties props) {
        this.coins = coins;
        this.notifications = notifications;
        this.feed = feed;
        this.users = users;
        this.jdbc = jdbc;
        this.events = events;
        this.clock = clock;
        this.bankDelaySeconds = props.demo() != null ? props.demo().bankDelaySeconds() : 60;
    }

    @Transactional
    public ConversionCreated create(long userId, ConversionRequest req) {
        Integer n = req == null ? null : req.coins();
        if (n == null || n < MIN_COINS || n % STEP != 0) {
            throw ApiException.validation("coins: minimum " + MIN_COINS + " və " + STEP + "-ə bölünən olmalıdır");
        }
        var card = req.cardMask() == null || req.cardMask().isBlank() ? DEFAULT_CARD : req.cardMask().trim();
        if (card.length() > 30) throw ApiException.validation("cardMask: ən çox 30 simvol");
        var azn = coins.manat(n);
        var kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into coin_conversions (user_id, coins, azn, card_mask, status, created_at, bank_due_at) "
                    + "values (?, ?, ?, ?, 'PENDING', ?, ?)", new String[]{"id"});
            ps.setLong(1, userId);
            ps.setInt(2, n);
            ps.setBigDecimal(3, new BigDecimal(azn));
            ps.setString(4, card);
            ps.setObject(5, clock.now());
            // wall clock, not the demo clock (which restarts at clock-start on every boot)
            ps.setObject(6, OffsetDateTime.now().plusSeconds(bankDelaySeconds));
            return ps;
        }, kh);
        long id = kh.getKey().longValue();
        // throws COINS_INSUFFICIENT → the whole transaction (incl. the conversion row) rolls back
        coins.spend(userId, n, "CONVERSION", "conv:" + id, "Banka çevirmə: " + azn.replace('.', ',') + " ₼");
        publish(row(id));
        return new ConversionCreated(id, n, azn, "PENDING", users.coinBalance(userId));
    }

    /** Bank simulation: completes PENDING conversions whose bank_due_at (wall clock) has passed. Returns how many. */
    @Transactional
    public int completeDue(OffsetDateTime wallNow) {
        var due = jdbc.queryForList("select id from coin_conversions where status = 'PENDING' and bank_due_at <= ? order by id",
                Long.class, wallNow);
        int done = 0;
        for (long id : due) {
            if (jdbc.update("update coin_conversions set status = 'COMPLETED', completed_at = ? where id = ? and status = 'PENDING'",
                    clock.now(), id) == 0) continue;
            long userId = jdbc.queryForObject("select user_id from coin_conversions where id = ?", Long.class, id);
            var r = row(id);
            var azn = r.azn().replace('.', ',');
            notifications.create(userId, "COIN", "Bank çevirməsi tamamlandı",
                    r.coins() + " Yolçu Coin → " + azn + " ₼ kartınıza köçürüldü (" + r.cardMask() + ")", "wallet");
            feed.add("COIN", "Bank çevirməsi tamamlandı: " + azn + " ₼",
                    r.userName() + ", " + r.coins() + " Yolçu Coin, Partnyor bank " + r.cardMask(), "p-coins");
            publish(r);
            done++;
        }
        if (done > 0) log.info("BankSim: {} conversion(s) completed", done);
        return done;
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversionRow> page(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ApiException.validation("page >= 0, size 1..100");
        // by id, not createdAt: the demo clock restarts on every boot, so createdAt can run backwards across restarts
        var items = jdbc.query(SELECT + "order by c.id desc limit ? offset ?", ConversionService::map, size, (long) page * size);
        long total = jdbc.queryForObject("select count(*) from coin_conversions", Long.class);
        return new PageResponse<>(items, page, size, total);
    }

    /** Demo: the demo-trigger passenger gets DEMO_COINS (1000) (reason DEMO, hidden from wallet history) and converts them. */
    @Transactional
    public ConversionCreated demoConversion() {
        long userId = demoUserId();
        coins.award(userId, DEMO_COINS, "DEMO", "demo-conv:" + System.nanoTime(), "Demo: toplanmış Yolçu Coin");
        return create(userId, new ConversionRequest(DEMO_COINS, DEMO_CARD));
    }

    public long demoUserId() {
        var ids = jdbc.queryForList("select id from users where phone = ?", Long.class, DEMO_PHONE);
        if (!ids.isEmpty()) return ids.get(0);
        var kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            var ps = con.prepareStatement("insert into users (phone, full_name, role, synthetic) values (?, ?, 'PASSENGER', false)",
                    new String[]{"id"});
            ps.setString(1, DEMO_PHONE);
            ps.setString(2, DEMO_NAME);
            return ps;
        }, kh);
        return kh.getKey().longValue();
    }

    /**
     * design/web_dashboard.html CONV: 3 COMPLETED + 2 PENDING for seed passengers (not the demo passenger). Runs on any DB
     * whose coin_conversions is empty. Ledger stays consistent: +coins SEED and −coins CONVERSION (net 0). Seeded PENDING rows
     * have bank_due_at = null, so BankSimJob never completes them (pilot rows "awaiting the bank" for the whole demo).
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seedIfEmpty() {
        if (jdbc.queryForObject("select count(*) from coin_conversions", Long.class) > 0) return;
        var userIds = jdbc.queryForList("select id from users where role = 'PASSENGER' and synthetic = false and phone is not null "
                + "and id > (select min(id) from users where role = 'PASSENGER') order by id limit 5", Long.class);
        if (userIds.size() < 5) return;
        var now = clock.now().truncatedTo(ChronoUnit.MINUTES);
        // oldest first, so ids grow with time; {coins, card, completed?, minutes ago}
        Object[][] rows = {{2000, "•• 6620", true, 1440}, {800, "•• 1205", false, 180}, {300, "•• 7781", true, 120},
                {1200, "•• 4410", false, 25}, {500, "•• 0193", true, 12}};
        for (int i = 0; i < rows.length; i++) {
            var r = rows[i];
            long uid = userIds.get(rows.length - 1 - i);
            int c = (Integer) r[0];
            var at = now.minusMinutes((Integer) r[3]);
            var azn = coins.manat(c);
            var kh = new GeneratedKeyHolder();
            jdbc.update(con -> {
                var ps = con.prepareStatement("insert into coin_conversions (user_id, coins, azn, card_mask, status, created_at, completed_at) "
                        + "values (?, ?, ?, ?, ?, ?, ?)", new String[]{"id"});
                ps.setLong(1, uid);
                ps.setInt(2, c);
                ps.setBigDecimal(3, new BigDecimal(azn));
                ps.setString(4, (String) r[1]);
                ps.setString(5, (Boolean) r[2] ? "COMPLETED" : "PENDING");
                ps.setObject(6, at);
                ps.setObject(7, (Boolean) r[2] ? at.plusMinutes(1) : null);
                return ps;
            }, kh);
            long id = kh.getKey().longValue();
            var tx = "insert into coin_tx (user_id, amount, reason, ref_id, label, created_at) values (?, ?, ?, ?, ?, ?)";
            jdbc.update(tx, uid, c, "SEED", "seed-conv:" + id, "Başlanğıc balansı", at.minusMinutes(1));
            jdbc.update(tx, uid, -c, "CONVERSION", "conv:" + id, "Banka çevirmə: " + azn.replace('.', ',') + " ₼", at);
        }
        log.info("Seeded {} coin conversions", rows.length);
    }

    ConversionRow row(long id) {
        return jdbc.queryForObject(SELECT + "where c.id = ?", ConversionService::map, id);
    }

    private void publish(ConversionRow r) {
        events.publishEvent(StreamEvent.toRole(Role.DISPATCHER, "conversion.updated", r));
        events.publishEvent(StreamEvent.toRole(Role.ADMIN, "conversion.updated", r));
    }

    private static ConversionRow map(ResultSet rs, int i) throws SQLException {
        return new ConversionRow(rs.getLong("id"), maskName(rs.getString("full_name")), maskPhone(rs.getString("phone")),
                rs.getInt("coins"), rs.getBigDecimal("azn").setScale(2).toPlainString(), rs.getString("card_mask"), rs.getString("status"),
                AppClock.local(rs.getObject("created_at", OffsetDateTime.class)), AppClock.local(rs.getObject("completed_at", OffsetDateTime.class)));
    }

    /** "Elvin Məmmədov" → "Elvin M." */
    static String maskName(String full) {
        if (full == null) return null;
        var p = full.trim().split("\\s+");
        return p.length < 2 ? p[0] : p[0] + " " + p[p.length - 1].charAt(0) + ".";
    }

    /** "+994 55 123 45 08" → "+994 55 ••• 08" */
    static String maskPhone(String phone) {
        if (phone == null) return null;
        var digits = phone.replaceAll("\\D", "");
        return phone.length() < 9 || digits.length() < 2 ? phone : phone.substring(0, 7) + " ••• " + digits.substring(digits.length() - 2);
    }
}
