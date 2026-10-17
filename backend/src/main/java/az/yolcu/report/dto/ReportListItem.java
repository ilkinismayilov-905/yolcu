package az.yolcu.report.dto;

import java.time.OffsetDateTime;

public record ReportListItem(String code, OffsetDateTime createdAt, String route, String bort, String operator,
                             String category, String textPreview, String status, boolean late, String place) {}
