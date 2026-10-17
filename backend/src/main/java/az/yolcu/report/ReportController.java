package az.yolcu.report;

import az.yolcu.common.PageResponse;
import az.yolcu.report.dto.CreateReportRequest;
import az.yolcu.report.dto.ReportCreated;
import az.yolcu.report.dto.ReportDetail;
import az.yolcu.report.dto.ReportListItem;
import az.yolcu.report.dto.ReportStats;
import az.yolcu.report.dto.ResolveRequest;
import az.yolcu.report.dto.ShareRequest;
import az.yolcu.report.dto.StatusRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Passenger (/me/reports) and dispatcher (/admin/reports) endpoints; roles are enforced in SecurityConfig. */
@RestController
@RequestMapping("/api/v1")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @PostMapping("/me/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportCreated create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateReportRequest req) {
        return service.create(userId, req);
    }

    @GetMapping("/me/reports")
    public PageResponse<ReportListItem> mine(@AuthenticationPrincipal Long userId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.mine(userId, page, size);
    }

    @GetMapping("/me/reports/{code}")
    public ReportDetail mine(@AuthenticationPrincipal Long userId, @PathVariable String code) {
        return service.mine(userId, code);
    }

    @GetMapping("/admin/reports")
    public PageResponse<ReportListItem> list(@RequestParam(required = false) String status,
                                             @RequestParam(required = false) String category,
                                             @RequestParam(required = false) String operator,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return service.list(status, category, operator, q, page, size);
    }

    @GetMapping("/admin/reports/export.csv")
    public ResponseEntity<String> exportCsv(@RequestParam(required = false) String status,
                                            @RequestParam(required = false) String category,
                                            @RequestParam(required = false) String operator,
                                            @RequestParam(required = false) String q) {
        var csv = service.exportCsv(status, category, operator, q);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sikayetler-" + csv.date() + ".csv\"")
                .header("X-Total-Count", String.valueOf(csv.rows()))
                .body(csv.body());
    }

    @GetMapping("/admin/reports/stats")
    public ReportStats stats() {
        return service.stats();
    }

    @GetMapping("/admin/reports/{code}")
    public ReportDetail detail(@PathVariable String code) {
        return service.adminDetail(code);
    }

    @PatchMapping("/admin/reports/{code}/status")
    public ReportDetail status(@AuthenticationPrincipal Long userId, @PathVariable String code,
                               @Valid @RequestBody StatusRequest req) {
        return service.changeStatus(userId, code, req);
    }

    @PostMapping("/admin/reports/{code}/forward")
    public ReportDetail forward(@AuthenticationPrincipal Long userId, @PathVariable String code) {
        return service.forward(userId, code);
    }

    @PostMapping("/admin/reports/{code}/resolve")
    public ReportDetail resolve(@AuthenticationPrincipal Long userId, @PathVariable String code,
                                @Valid @RequestBody(required = false) ResolveRequest req) {
        return service.resolve(userId, code, req);
    }

    @PostMapping("/admin/reports/{code}/share")
    public Map<String, Boolean> share(@AuthenticationPrincipal Long userId, @PathVariable String code,
                                      @Valid @RequestBody ShareRequest req) {
        service.share(userId, code, req);
        return Map.of("ok", true);
    }
}
