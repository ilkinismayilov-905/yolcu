package az.yolcu.report.dto;

import java.time.OffsetDateTime;

public record ReportEventDto(String type, String note, String actor, OffsetDateTime createdAt) {}
