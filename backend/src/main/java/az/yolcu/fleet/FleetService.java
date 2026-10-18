package az.yolcu.fleet;

import az.yolcu.common.ApiException;
import az.yolcu.common.AppClock;
import az.yolcu.common.PageResponse;
import az.yolcu.fleet.dto.BusDetail;
import az.yolcu.fleet.dto.BusListItem;
import az.yolcu.fleet.dto.BusReview;
import az.yolcu.fleet.dto.OperatorStats;
import az.yolcu.fleet.dto.SearchResult;
import az.yolcu.report.ReportService;
import az.yolcu.sim.SimulationService;
import az.yolcu.sim.dto.BusPosition;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BE-9.3/9.4: operator cards, fleet table, bus detail, global search. Seeded tables + live sim occupancy. */
@Service
@Transactional(readOnly = true)
public class FleetService {
    static final int DAYS = 30;
    static final Set<String> STATUSES = Set.of("LINE", "DEPOT", "REPAIR");
    static final Set<String> TYPES = Set.of("ELECTRIC", "DIESEL");
    private static final Pattern REPORT_CODE = Pattern.compile("#?A-?(\\d{1,8})", Pattern.CASE_INSENSITIVE);
    /** Bort without dashes/spaces, upper case: "10 bb 482" and "10BB482" both find 10-BB-482. */
    private static final String NORM_BORT = "replace(upper(b.bort), '-', '')";
    private static final String BUS_SELECT = "select b.bort, o.name, o.code, b.route, b.type, b.model, b.capacity, b.status, "
            + "(select avg(cast(r.driver_rating as double precision)) from reviews r where r.bort = b.bort) drv "
            + "from buses b join operators o on o.id = b.operator_id ";

    private final JdbcTemplate jdbc;
    private final SimulationService sim;
    private final ReportService reports;
    private final AppClock clock;

    public FleetService(JdbcTemplate jdbc, SimulationService sim, ReportService reports, AppClock clock) {
        this.jdbc = jdbc;
        this.sim = sim;
        this.reports = reports;
        this.clock = clock;
    }

    /**
     * avgRating: mean of (driver + road + bus) / 3 over all the operator's reviews. complaintsPer10k: reports about the
     * operator ÷ trips on its buses (trips.bort → buses), both in the last 30 days of the demo clock, × 10 000.
     */
    public List<OperatorStats> operators() {
        OffsetDateTime since = clock.now().minusDays(DAYS);
        var rating = doubles("select operator_id, avg((driver_rating + road_rating + bus_rating) / 3.0) from reviews "
                + "where operator_id is not null group by operator_id");
        var complaints = doubles("select operator_id, count(*) from reports where operator_id is not null and created_at >= ? "
                + "group by operator_id", since);
        var trips = doubles("select b.operator_id, count(*) from trips t join buses b on b.bort = t.bort where t.started_at >= ? "
                + "group by b.operator_id", since);
        var buses = new HashMap<Long, int[]>(); // operator_id → {total, line}
        jdbc.query("select operator_id, status from buses", rs -> {
            var c = buses.computeIfAbsent(rs.getLong(1), k -> new int[2]);
            c[0]++;
            if ("LINE".equals(rs.getString(2))) c[1]++;
        });
        return jdbc.query("select id, code, name, fleet_total, punctuality, routes from operators order by id", (rs, i) -> {
            long id = rs.getLong("id");
            int fleet = rs.getInt("fleet_total");
            var c = buses.getOrDefault(id, new int[2]);
            // shortcut: fleet_total is the seeded city-scale fleet (640 for BakuBus) but only 66 buses are in the DB;
            // onLine = fleet_total × LINE share of the seeded buses. Replace with a real count when the full fleet is imported.
            int onLine = c[0] == 0 ? 0 : (int) Math.round(fleet * (double) c[1] / c[0]);
            // shortcut: punctuality is the seeded operators.punctuality (mocks/operators.json), no schedule-adherence source yet
            int punctuality = rs.getInt("punctuality");
            double t = trips.getOrDefault(id, 0.0);
            double per10k = t == 0 ? 0 : round1(complaints.getOrDefault(id, 0.0) / t * 10_000);
            var r = rating.get(id);
            var routes = rs.getString("routes");
            return new OperatorStats(id, rs.getString("code"), rs.getString("name"), fleet, onLine,
                    r == null ? null : round1(r), per10k, punctuality,
                    routes == null || routes.isBlank() ? List.of() : Arrays.asList(routes.split(",")));
        });
    }

    /** Fleet table, sorted by bort. q = case-insensitive substring of bort or route. */
    public PageResponse<BusListItem> buses(String operator, String status, String type, String q, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ApiException.validation("page >= 0, size 1..100");
        var where = new ArrayList<String>();
        var args = new ArrayList<Object>();
        if (!blank(operator)) {
            var codes = jdbc.queryForList("select code from operators", String.class);
            if (!codes.contains(operator.trim())) throw ApiException.validation("operator: " + String.join(" | ", codes));
            where.add("o.code = ?");
            args.add(operator.trim());
        }
        if (!blank(status)) {
            if (!STATUSES.contains(status.trim())) throw ApiException.validation("status: LINE | DEPOT | REPAIR");
            where.add("b.status = ?");
            args.add(status.trim());
        }
        if (!blank(type)) {
            if (!TYPES.contains(type.trim())) throw ApiException.validation("type: ELECTRIC | DIESEL");
            where.add("b.type = ?");
            args.add(type.trim());
        }
        if (!blank(q)) {
            if (q.trim().length() > 40) throw ApiException.validation("q: ən çox 40 simvol");
            var like = "%" + q.trim().toLowerCase() + "%";
            where.add("(lower(b.bort) like ? or lower(b.route) like ?)");
            args.add(like);
            args.add(like);
        }
        var filter = where.isEmpty() ? "" : "where " + String.join(" and ", where) + " ";
        Long total = jdbc.queryForObject("select count(*) from buses b join operators o on o.id = b.operator_id " + filter,
                Long.class, args.toArray());
        var occ = occupancy();
        var pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        var items = jdbc.query(BUS_SELECT + filter + "order by b.bort limit ? offset ?", (rs, i) -> row(rs, occ), pageArgs.toArray());
        return new PageResponse<>(items, page, size, total == null ? 0 : total);
    }

    public BusDetail bus(String bort) {
        var b = findBus(bort);
        if (b == null) throw ApiException.notFound("Avtobus tapılmadı");
        Integer count = jdbc.queryForObject("select count(*) from reviews where bort = ?", Integer.class, b.bort());
        var reviews = jdbc.query("select driver_rating, road_rating, bus_rating, crowd_level, tags, comment, created_at "
                + "from reviews where bort = ? order by created_at desc, id desc limit 5", (rs, i) -> new BusReview(
                rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getString(4), tags(rs.getString(5)), rs.getString(6),
                AppClock.local(rs.getObject(7, OffsetDateTime.class))), b.bort());
        return BusDetail.of(b, count == null ? 0 : count, reports.latestForBort(b.bort()), reviews);
    }

    /** Bort (dash/space/case tolerant) → BUS; A-2041 / #A-2041 / a2041 → REPORT if it exists; else NONE. */
    public SearchResult search(String q) {
        if (blank(q)) return new SearchResult("NONE", null);
        var m = REPORT_CODE.matcher(q.trim());
        if (m.matches()) {
            var code = "A-" + m.group(1);
            var found = jdbc.queryForList("select code from reports where code = ?", String.class, code);
            if (!found.isEmpty()) return new SearchResult("REPORT", found.get(0));
        }
        var b = findBus(q);
        return b != null ? new SearchResult("BUS", b.bort()) : new SearchResult("NONE", null);
    }

    private BusListItem findBus(String bort) {
        var norm = bort.replaceAll("[\\s-]", "").toUpperCase();
        if (norm.isEmpty()) return null;
        var occ = occupancy();
        var list = jdbc.query(BUS_SELECT + "where " + NORM_BORT + " = ?", (rs, i) -> row(rs, occ), norm);
        return list.isEmpty() ? null : list.get(0);
    }

    private Map<String, Integer> occupancy() {
        return sim.buses(null).stream().collect(Collectors.toMap(BusPosition::bort, BusPosition::occupancy, (a, b) -> a));
    }

    private static BusListItem row(java.sql.ResultSet rs, Map<String, Integer> occ) throws java.sql.SQLException {
        var status = rs.getString("status");
        double drv = rs.getDouble("drv");
        Double driver = rs.wasNull() ? null : round1(drv);
        return new BusListItem(rs.getString("bort"), rs.getString("name"), rs.getString("code"), rs.getString("route"),
                rs.getString("type"), rs.getString("model"), rs.getInt("capacity"), status,
                "LINE".equals(status) ? occ.get(rs.getString("bort")) : null, driver);
    }

    private Map<Long, Double> doubles(String sql, Object... args) {
        var out = new HashMap<Long, Double>();
        jdbc.query(sql, rs -> { out.put(rs.getLong(1), rs.getDouble(2)); }, args);
        return out;
    }

    /** reviews.tags is comma-separated text; the API (like POST review) uses an array. */
    private static List<String> tags(String s) {
        return s == null || s.isBlank() ? List.of() : Arrays.stream(s.split(",")).map(String::trim).filter(t -> !t.isEmpty()).toList();
    }

    private static double round1(double x) {
        return Math.round(x * 10) / 10.0;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
