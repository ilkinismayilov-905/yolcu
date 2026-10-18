package az.yolcu.report;

import az.yolcu.coin.CoinService;
import az.yolcu.common.ApiException;
import az.yolcu.common.AppClock;
import az.yolcu.common.Csv;
import az.yolcu.common.PageResponse;
import az.yolcu.fleet.Operator;
import az.yolcu.fleet.OperatorRepository;
import az.yolcu.geo.StopService;
import az.yolcu.notification.NotificationService;
import az.yolcu.report.dto.CreateReportRequest;
import az.yolcu.report.dto.ReportCreated;
import az.yolcu.report.dto.ReportDetail;
import az.yolcu.report.dto.ReportEventDto;
import az.yolcu.report.dto.ReportListItem;
import az.yolcu.report.dto.ReportStats;
import az.yolcu.report.dto.ResolveRequest;
import az.yolcu.report.dto.ShareRequest;
import az.yolcu.report.dto.StatusRequest;
import az.yolcu.stream.StreamEvent;
import az.yolcu.user.Role;
import az.yolcu.user.User;
import az.yolcu.user.UserRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReportService {
    public static final List<String> CATEGORIES = List.of("Dayanacaqda saxlamadı", "Kobud davranış", "Təhlükəli sürüş",
            "Telefonla danışırdı", "Kondisioner işləmir", "Çirkli salon", "Çox uzun gözləmə", "Əlçatanlıq problemi",
            "Ödəniş problemi", "Doluluq");
    public static final int RATE_LIMIT = 3;
    public static final Duration RATE_WINDOW = Duration.ofMinutes(10);
    static final Duration LATE_AFTER = Duration.ofHours(24);
    static final int COINS_CREATE = CoinService.REPORT, COINS_RESOLVED = CoinService.REPORT_VERIFIED;
    private static final String PASSENGER_ACTOR = "Sərnişin";
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ReportRepository reports;
    private final ReportEventRepository reportEvents;
    private final OperatorRepository operators;
    private final UserRepository users;
    private final StopService stops;
    private final CoinService coins;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;
    private final AppClock clock;

    public ReportService(ReportRepository reports, ReportEventRepository reportEvents, OperatorRepository operators,
                         UserRepository users, StopService stops, CoinService coins, NotificationService notifications,
                         ApplicationEventPublisher events, AppClock clock) {
        this.reports = reports;
        this.reportEvents = reportEvents;
        this.operators = operators;
        this.users = users;
        this.stops = stops;
        this.coins = coins;
        this.notifications = notifications;
        this.events = events;
        this.clock = clock;
    }

    // ---- passenger ----

    public ReportCreated create(long userId, CreateReportRequest req) {
        if (!CATEGORIES.contains(req.category())) throw ApiException.validation("category: naməlum kateqoriya");
        var now = clock.now();
        if (reports.countByUserIdAndCreatedAtAfter(userId, now.minus(RATE_WINDOW)) >= RATE_LIMIT) {
            throw ApiException.rateLimit("10 dəqiqədə ən çox 3 şikayət göndərmək olar. Bir az sonra yenidən cəhd edin");
        }
        var bort = blankToNull(req.bort());
        var place = req.lat() != null && req.lon() != null
                ? stops.near(req.lat(), req.lon(), 1000).stream().findFirst().map(s -> s.name()).orElse(null) : null;
        // shortcut: max+1 code, a concurrent duplicate fails on the unique index; fine for demo traffic
        var code = "A-" + (reports.maxCodeNumber() + 1);
        var r = reports.save(new Report(code, userId, req.route().trim(), bort, operatorFor(bort, req.route().trim()),
                req.category(), req.text(), Boolean.TRUE.equals(req.hasPhoto()), place, req.lat(), req.lon(), now));
        reportEvents.save(new ReportEvent(r.getId(), "CREATED", null, PASSENGER_ACTOR, now));
        int awarded = coins.award(userId, COINS_CREATE, "REPORT", "report:" + code, "Şikayət #" + code);

        var item = item(r, now);
        events.publishEvent(StreamEvent.toRole(Role.DISPATCHER, "report.created", item));
        events.publishEvent(StreamEvent.toRole(Role.ADMIN, "report.created", item));
        events.publishEvent(new ReportCreatedEvent(item));
        return new ReportCreated(detail(r, null), awarded);
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportListItem> mine(long userId, int page, int size) {
        var now = clock.now();
        return PageResponse.of(reports.findByUserId(userId, pageable(page, size)), r -> item(r, now));
    }

    @Transactional(readOnly = true)
    public ReportDetail mine(long userId, String code) {
        return detail(reports.findByCodeAndUserId(code, userId).orElseThrow(ReportService::notFound), null);
    }

    // ---- admin ----

    @Transactional(readOnly = true)
    public PageResponse<ReportListItem> list(String status, String category, String operator, String q, int page, int size) {
        var now = clock.now();
        return PageResponse.of(reports.findAll(spec(status, category, operator, q), pageable(page, size)), r -> item(r, now));
    }

    /** Last 5 reports about one bus (GET /admin/buses/{bort}). */
    @Transactional(readOnly = true)
    public List<ReportListItem> latestForBort(String bort) {
        var now = clock.now();
        return reports.findTop5ByBortOrderByCreatedAtDescIdDesc(bort).stream().map(r -> item(r, now)).toList();
    }

    public record CsvExport(String body, int rows, LocalDate date) {}

    /** Same filters as {@link #list}, no paging, newest first. */
    @Transactional(readOnly = true)
    public CsvExport exportCsv(String status, String category, String operator, String q) {
        var now = clock.now();
        var all = reports.findAll(spec(status, category, operator, q), NEWEST);
        var sb = new StringBuilder(Csv.BOM)
                .append(Csv.row("ID", "Vaxt", "Marşrut", "Bort", "Operator", "Mövzu", "Mətn", "Status", "Yer", "Gecikir"));
        for (var r : all) {
            var it = item(r, now);
            sb.append(Csv.row(it.code(), it.createdAt() == null ? null : CSV_TIME.format(it.createdAt()), it.route(),
                    it.bort(), it.operator(), it.category(), r.getText(), r.getStatus().label, it.place(),
                    it.late() ? "Bəli" : "Xeyr"));
        }
        return new CsvExport(sb.toString(), all.size(), now.atZoneSameInstant(AppClock.ZONE).toLocalDate());
    }

    @Transactional(readOnly = true)
    public ReportStats stats() {
        var now = clock.now();
        var today = now.atZoneSameInstant(AppClock.ZONE).truncatedTo(ChronoUnit.DAYS).toOffsetDateTime();
        return new ReportStats(
                reports.countByCreatedAtGreaterThanEqual(today),
                reports.countByStatusIn(List.of(ReportStatus.NEW, ReportStatus.REVIEW)),
                reports.countByStatusIn(List.of(ReportStatus.FORWARDED)),
                reports.countByStatusAndForwardedAtBefore(ReportStatus.FORWARDED, now.minus(LATE_AFTER)),
                reports.countByStatusIn(List.of(ReportStatus.NEW)));
    }

    @Transactional(readOnly = true)
    public ReportDetail adminDetail(String code) {
        return adminDetail(find(code));
    }

    public ReportDetail changeStatus(long actorId, String code, StatusRequest req) {
        ReportStatus next;
        try {
            next = ReportStatus.valueOf(req.status());
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("status: REVIEW | FORWARDED | ANSWERED | RESOLVED");
        }
        return adminDetail(transition(find(code), next, req.note(), actorId));
    }

    public ReportDetail forward(long actorId, String code) {
        return adminDetail(transition(find(code), ReportStatus.FORWARDED, null, actorId));
    }

    public ReportDetail resolve(long actorId, String code, ResolveRequest req) {
        return adminDetail(transition(find(code), ReportStatus.RESOLVED, req == null ? null : req.answerText(), actorId));
    }

    public void share(long actorId, String code, ShareRequest req) {
        var r = find(code);
        var now = clock.now();
        var note = String.join(", ", req.targets()) + (Boolean.FALSE.equals(req.hidePersonal()) ? "" : " (şəxsi məlumat gizli)");
        reportEvents.save(new ReportEvent(r.getId(), "SHARED", note, actorName(actorId), now));
        publishUpdated(r, now);
    }

    // ---- internals ----

    /** Status change: validates the transition, writes report_events, notifies the passenger, publishes report.updated. */
    private Report transition(Report r, ReportStatus next, String note, long actorId) {
        if (!r.getStatus().canMoveTo(next)) {
            throw ApiException.validation("Status keçidi mümkün deyil: " + r.getStatus() + " → " + next);
        }
        var now = clock.now();
        var actor = actorName(actorId);
        var userNote = blankToNull(note);
        var code = r.getCode();
        r.moveTo(next, now);
        String title, body;
        switch (next) {
            case REVIEW -> {
                reportEvents.save(new ReportEvent(r.getId(), "STATUS", statusNote(next, userNote), actor, now));
                title = "Şikayətinizə baxılır";
                body = "#" + code + ": AYNA şikayətinizi araşdırır";
            }
            case FORWARDED -> {
                reportEvents.save(new ReportEvent(r.getId(), "FORWARDED", userNote != null ? userNote : "Operatora göndərildi", actor, now));
                title = "Şikayətiniz operatora göndərildi";
                body = "#" + code + (r.getOperator() != null ? " " + r.getOperator().getName() + " operatoruna" : " operatora")
                        + " göndərildi. Cavab müddəti 24 saat";
            }
            case ANSWERED -> {
                if (userNote != null) r.setAnswerText(userNote);
                reportEvents.save(new ReportEvent(r.getId(), "ANSWERED", r.getAnswerText(), actor, now));
                title = "Şikayətinizə cavab verildi";
                body = r.getAnswerText() != null ? r.getAnswerText() : "#" + code + ": operator cavab verdi";
            }
            case RESOLVED -> {
                if (userNote != null) r.setAnswerText(userNote);
                reportEvents.save(new ReportEvent(r.getId(), "STATUS", statusNote(next, userNote), actor, now));
                title = "Şikayətiniz həll edildi";
                body = "#" + code + ": təşəkkür edirik! +" + COINS_RESOLVED + " Yolçu Coin hesabınıza əlavə olundu";
                if (r.getUserId() != null) {
                    coins.award(r.getUserId(), COINS_RESOLVED, "REPORT_VERIFIED", "report-ok:" + code, "Təsdiqlənmiş şikayət #" + code);
                }
            }
            default -> throw new IllegalStateException();
        }
        if (r.getUserId() != null) notifications.create(r.getUserId(), "REPORT", title, body, "report:" + code);
        publishUpdated(r, now);
        return r;
    }

    private void publishUpdated(Report r, OffsetDateTime now) {
        var item = item(r, now);
        events.publishEvent(StreamEvent.toRole(Role.DISPATCHER, "report.updated", item));
        events.publishEvent(StreamEvent.toRole(Role.ADMIN, "report.updated", item));
        if (r.getUserId() != null) events.publishEvent(StreamEvent.toUser(r.getUserId(), "report.updated", item));
    }

    /** STATUS notes follow the seed: the status name, optionally ": <dispatcher note>". */
    private static String statusNote(ReportStatus s, String userNote) {
        return userNote == null ? s.name() : s.name() + ": " + userNote;
    }

    private Report find(String code) {
        return reports.findByCode(code).orElseThrow(ReportService::notFound);
    }

    private static ApiException notFound() {
        return ApiException.notFound("Şikayət tapılmadı");
    }

    private Operator operatorFor(String bort, String route) {
        var byBort = bort == null ? null : operators.findIdByBort(bort).flatMap(operators::findById).orElse(null);
        if (byBort != null) return byBort;
        return operators.findAll().stream()
                .filter(o -> o.getRoutes() != null && Arrays.asList(o.getRoutes().split(",")).contains(route))
                .findFirst().orElse(null);
    }

    private String actorName(long userId) {
        return users.findById(userId).map(User::getFullName).orElse("AYNA");
    }

    private ReportDetail adminDetail(Report r) {
        return detail(r, "Gizli");
    }

    private ReportDetail detail(Report r, String passenger) {
        var evs = reportEvents.findByReportIdOrderByCreatedAtAscIdAsc(r.getId()).stream()
                .map(e -> new ReportEventDto(e.getType(), e.getNote(), e.getActor(), AppClock.local(e.getCreatedAt())))
                .toList();
        int given = r.getUserId() == null ? 0
                : coins.sumFor(r.getUserId(), List.of("report:" + r.getCode(), "report-ok:" + r.getCode()));
        return ReportDetail.of(item(r, clock.now()), r.getText(), r.isHasPhoto(), given, r.getAnswerText(), evs, passenger);
    }

    private static ReportListItem item(Report r, OffsetDateTime now) {
        boolean late = r.getStatus() == ReportStatus.FORWARDED && r.getForwardedAt() != null
                && r.getForwardedAt().isBefore(now.minus(LATE_AFTER));
        var text = r.getText() == null ? "" : r.getText();
        var preview = text.length() > 80 ? text.substring(0, 79).trim() + "…" : text;
        return new ReportListItem(r.getCode(), AppClock.local(r.getCreatedAt()), r.getRoute(), r.getBort(),
                r.getOperator() != null ? r.getOperator().getName() : null, r.getCategory(), preview, r.getStatus().name(),
                late, r.getPlace());
    }

    private static Specification<Report> spec(String status, String category, String operator, String q) {
        var statuses = blankToNull(status) == null ? null : statusFilter(status);
        return (root, query, cb) -> {
            var where = new ArrayList<Predicate>();
            var op = root.join("operator", JoinType.LEFT);
            if (statuses != null) where.add(root.get("status").in(statuses));
            if (blankToNull(category) != null) where.add(cb.equal(root.get("category"), category));
            if (blankToNull(operator) != null) where.add(cb.equal(op.get("code"), operator));
            if (blankToNull(q) != null) {
                var like = "%" + q.trim().toLowerCase().replace("#", "") + "%";
                where.add(cb.or(cb.like(cb.lower(root.get("code")), like), cb.like(cb.lower(root.get("bort")), like),
                        cb.like(cb.lower(root.get("route")), like), cb.like(cb.lower(root.get("text")), like),
                        cb.like(cb.lower(op.get("name")), like)));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static java.util.Set<ReportStatus> statusFilter(String status) {
        try {
            return ReportStatus.filter(status);
        } catch (IllegalArgumentException e) {
            throw ApiException.validation("status: NEW | REVIEW | FORWARDED | CLOSED");
        }
    }

    private static PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw ApiException.validation("page >= 0, size 1..100");
        return PageRequest.of(page, size, NEWEST);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
