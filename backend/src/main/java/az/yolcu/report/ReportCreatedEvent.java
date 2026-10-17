package az.yolcu.report;

import az.yolcu.report.dto.ReportListItem;

/** Domain event after a passenger report is saved (same transaction); the feed listens to it. */
public record ReportCreatedEvent(ReportListItem item) {}
