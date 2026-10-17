package az.yolcu.report.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** POST /me/reports → ReportDetail fields + coinsAwarded. */
public record ReportCreated(@JsonUnwrapped ReportDetail report, int coinsAwarded) {}
