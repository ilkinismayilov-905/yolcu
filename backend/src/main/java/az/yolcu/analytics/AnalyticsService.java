package az.yolcu.analytics;

import az.yolcu.analytics.dto.AlightingPoint;
import az.yolcu.analytics.dto.AnalyticsKpis;
import az.yolcu.analytics.dto.OdMatrix;
import az.yolcu.common.ApiException;
import az.yolcu.common.AppClock;
import az.yolcu.geo.StopService;
import az.yolcu.seed.SyntheticGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BE-9.1: analytics from the trips table (real + synthetic seed), last 30 days of the demo clock, Baku local time. */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    static final int DAYS = 30, MAX_LIMIT = 50;
    // shortcut: suggestion accept/decline is not stored yet, the design's 41 % is returned; replace when it is tracked
    static final int SUGGESTION_ACCEPTANCE = 41;
    private static final Pattern HHMM = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");
    /** Minute of the Baku day (UTC+4, no DST) — same SQL in Postgres and H2. */
    private static final String LOCAL_MIN = "mod(cast(extract(epoch from %s) as bigint) + 14400, 86400) / 60";

    private final JdbcTemplate jdbc;
    private final StopService stops;
    private final AppClock clock;

    public AnalyticsService(JdbcTemplate jdbc, StopService stops, AppClock clock) {
        this.jdbc = jdbc;
        this.stops = stops;
        this.clock = clock;
    }

    public AnalyticsKpis kpis() {
        var since = clock.now().minusDays(DAYS);
        var row = jdbc.queryForMap("select count(*) n, avg(extract(epoch from ended_at) - extract(epoch from started_at)) dur "
                + "from trips where started_at >= ?", since);
        Long users = jdbc.queryForObject("select count(distinct t.user_id) from trips t join users u on u.id = t.user_id "
                + "where u.tracking_consent = true and t.started_at >= ?", Long.class, since);
        // longest wait at a transfer = worst case for a missed bus = the longest headway in route_config
        Integer wait = jdbc.queryForObject("select max(interval_min) from route_config", Integer.class);
        var dur = (Number) row.get("dur");
        return new AnalyticsKpis(((Number) row.get("n")).longValue(), users, dur == null ? 0 : (int) Math.round(dur.doubleValue() / 60),
                wait == null ? 0 : wait, SUGGESTION_ACCEPTANCE);
    }

    /** Trips started in [from, to) local time between the 6 analytics zones (boarding stop → destination stop). */
    public OdMatrix od(String from, String to) {
        int a = minute("from", from), b = minute("to", to);
        if (a >= b) throw ApiException.validation("from: 'to'-dan əvvəl olmalıdır");
        var ids = Arrays.asList(SyntheticGenerator.ZONE_STOPS);
        var m = new long[ids.size()][ids.size()];
        var min = LOCAL_MIN.formatted("started_at");
        jdbc.query("select from_stop, to_place, count(*) from trips where started_at >= ? and " + min + " >= ? and " + min
                + " < ? group by from_stop, to_place", rs -> {
            int i = ids.indexOf(rs.getString(1)), j = ids.indexOf(rs.getString(2));
            if (i >= 0 && j >= 0) m[i][j] = rs.getLong(3);
        }, clock.now().minusDays(DAYS), a, b);
        return new OdMatrix(List.of(SyntheticGenerator.ZONE_NAMES), m);
    }

    /** Top alighting stops: per-day average, busiest 5-minute slot, main origin and routes. */
    public List<AlightingPoint> alighting(int limit) {
        if (limit < 1 || limit > MAX_LIMIT) throw ApiException.validation("limit: 1–" + MAX_LIMIT);
        var since = clock.now().minusDays(DAYS);
        var top = new LinkedHashMap<String, Long>();
        jdbc.query("select alight_stop, count(*) n from trips where alight_stop is not null and started_at >= ? "
                + "group by alight_stop order by n desc, alight_stop limit ?", rs -> { top.put(rs.getString(1), rs.getLong(2)); }, since, limit);
        var peak = best("select s, slot, count(*) from (select alight_stop s, " + LOCAL_MIN.formatted("ended_at") + " / 5 slot "
                + "from trips where alight_stop is not null and ended_at is not null and started_at >= ?) x group by s, slot", since);
        var origin = best("select alight_stop, from_stop, count(*) from trips where alight_stop is not null and started_at >= ? "
                + "group by alight_stop, from_stop", since);
        var routes = new HashMap<String, List<String>>(); // stop → routes by trips desc
        jdbc.query("select alight_stop, route, count(*) n from trips where alight_stop is not null and started_at >= ? "
                + "group by alight_stop, route order by n desc, route", rs -> {
            routes.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(rs.getString(2));
        }, since);
        var out = new ArrayList<AlightingPoint>();
        top.forEach((stop, n) -> {
            var slot = peak.get(stop);
            var hhmm = slot == null ? null : "%02d:%02d".formatted(Integer.parseInt(slot) * 5 / 60, Integer.parseInt(slot) * 5 % 60);
            var from = origin.get(stop);
            var fromName = from == null ? null : stops.byId(from).map(s -> s.name()).orElse(from);
            var rs = routes.getOrDefault(stop, List.of());
            var note = (fromName != null ? fromName + " → " + stop + " axını" : stop)
                    + (rs.isEmpty() ? "" : ", " + String.join(" və ", rs.subList(0, Math.min(2, rs.size()))));
            out.add(new AlightingPoint(stop, Math.round(n / (double) DAYS), hhmm, note));
        });
        return out;
    }

    /** "key, value, count" rows → key → value with the highest count (ties: first seen). */
    private Map<String, String> best(String sql, Object... args) {
        var best = new HashMap<String, String>();
        var max = new HashMap<String, Long>();
        jdbc.query(sql, rs -> {
            var k = rs.getString(1);
            long c = rs.getLong(3);
            if (c > max.getOrDefault(k, -1L)) {
                max.put(k, c);
                best.put(k, rs.getString(2));
            }
        }, args);
        return best;
    }

    private static int minute(String name, String v) {
        if (v == null || !HHMM.matcher(v).matches()) throw ApiException.validation(name + ": HH:mm formatında olmalıdır");
        return Integer.parseInt(v.substring(0, 2)) * 60 + Integer.parseInt(v.substring(3));
    }
}
