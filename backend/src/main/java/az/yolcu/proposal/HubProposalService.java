package az.yolcu.proposal;

import az.yolcu.common.ApiException;
import az.yolcu.common.AppClock;
import az.yolcu.common.GeoUtil;
import az.yolcu.feed.FeedService;
import az.yolcu.proposal.dto.HubProposal;
import az.yolcu.stream.StreamEvent;
import az.yolcu.user.Role;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** BE-9.2: hub_proposals storage, NEW → ACCEPTED | REJECTED, proposal.created SSE + AI alert. */
@Service
@Transactional
public class HubProposalService {
    static final List<String> STATUSES = List.of("NEW", "ACCEPTED", "REJECTED");
    public static final String ALERT_PREFIX = "Yeni hub təklifi: ";

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final FeedService feed;
    private final ApplicationEventPublisher events;
    private final AppClock clock;

    public HubProposalService(JdbcTemplate jdbc, ObjectMapper mapper, FeedService feed, ApplicationEventPublisher events,
                              AppClock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.feed = feed;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<HubProposal> list(String status) {
        if (status == null || status.isBlank()) return jdbc.query("select * from hub_proposals order by created_at desc, id desc", this::map);
        if (!STATUSES.contains(status)) throw ApiException.validation("status: NEW | ACCEPTED | REJECTED");
        return jdbc.query("select * from hub_proposals where status = ? order by created_at desc, id desc", this::map, status);
    }

    public HubProposal accept(long id) { return transition(id, "ACCEPTED"); }

    public HubProposal reject(long id) { return transition(id, "REJECTED"); }

    /** True if any proposal (any status) lies within meters of the point: the rule proposes a place only once. */
    @Transactional(readOnly = true)
    public boolean existsNear(double lat, double lon, double meters) {
        return jdbc.query("select lat, lon from hub_proposals", (rs, i) -> GeoUtil.distanceMeters(lat, lon, rs.getDouble(1), rs.getDouble(2)))
                .stream().anyMatch(d -> d <= meters);
    }

    public HubProposal create(String name, double lat, double lon, int jamMinutes, int routesCount, int metroM, int waiting,
                              int paxPerHour, int confidence, List<String> items, HubProposal.Impact impact) {
        var keys = new GeneratedKeyHolder();
        var now = clock.now();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement("insert into hub_proposals (name, lat, lon, jam_minutes, routes_count, "
                    + "metro_m, waiting, pax_per_hour, confidence, items, impact, status, created_at) "
                    + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'NEW', ?)", new String[]{"id"});
            ps.setString(1, name);
            ps.setDouble(2, lat);
            ps.setDouble(3, lon);
            ps.setInt(4, jamMinutes);
            ps.setInt(5, routesCount);
            ps.setInt(6, metroM);
            ps.setInt(7, waiting);
            ps.setInt(8, paxPerHour);
            ps.setInt(9, confidence);
            ps.setString(10, json(items));
            ps.setString(11, json(impact));
            ps.setObject(12, now);
            return ps;
        }, keys);
        var p = get(keys.getKey().longValue());
        events.publishEvent(StreamEvent.toRole(Role.DISPATCHER, "proposal.created", p));
        events.publishEvent(StreamEvent.toRole(Role.ADMIN, "proposal.created", p));
        feed.add("AI", ALERT_PREFIX + name, "Tıxac " + jamMinutes + " dəq, " + routesCount + " marşrut, metroya " + metroM
                + " m, gözləyən " + waiting + " nəfər. Etibarlılıq " + confidence + "%", "p-map");
        return p;
    }

    private HubProposal transition(long id, String to) {
        var p = get(id);
        if (!p.status().equals("NEW")) throw ApiException.validation("Təklif artıq " + p.status() + " statusundadır");
        jdbc.update("update hub_proposals set status = ? where id = ?", to, id);
        return get(id);
    }

    private HubProposal get(long id) {
        return jdbc.query("select * from hub_proposals where id = ?", this::map, id).stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("Hub təklifi tapılmadı: " + id));
    }

    private HubProposal map(ResultSet rs, int i) throws SQLException {
        try {
            int metroM = rs.getInt("metro_m");
            return new HubProposal(rs.getLong("id"), rs.getString("name"), rs.getDouble("lat"), rs.getDouble("lon"),
                    rs.getInt("jam_minutes"), rs.getInt("routes_count"), metroM, rs.getInt("waiting"),
                    rs.getInt("pax_per_hour"), rs.getInt("confidence"),
                    mapper.readValue(rs.getString("items"), new TypeReference<List<String>>() {}),
                    mapper.readValue(rs.getString("impact"), HubProposal.Impact.class), rs.getString("status"),
                    HubProposal.reason(metroM), AppClock.local(rs.getObject("created_at", OffsetDateTime.class)));
        } catch (JsonProcessingException e) {
            throw new SQLException("hub_proposals " + rs.getLong("id") + ": bad items/impact JSON", e);
        }
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
