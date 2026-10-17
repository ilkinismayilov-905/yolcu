package az.yolcu.report;

import java.util.Set;

public enum ReportStatus {
    NEW, REVIEW, FORWARDED, ANSWERED, RESOLVED;

    /** NEW → REVIEW → FORWARDED → ANSWERED/RESOLVED, NEW → FORWARDED, ANSWERED → RESOLVED. */
    public boolean canMoveTo(ReportStatus next) {
        return switch (this) {
            case NEW -> next == REVIEW || next == FORWARDED;
            case REVIEW -> next == FORWARDED;
            case FORWARDED -> next == ANSWERED || next == RESOLVED;
            case ANSWERED -> next == RESOLVED;
            case RESOLVED -> false;
        };
    }

    /** Admin list filter value → statuses (CLOSED = ANSWERED + RESOLVED). */
    public static Set<ReportStatus> filter(String value) {
        return switch (value) {
            case "NEW" -> Set.of(NEW);
            case "REVIEW" -> Set.of(REVIEW);
            case "FORWARDED" -> Set.of(FORWARDED);
            case "CLOSED" -> Set.of(ANSWERED, RESOLVED);
            default -> throw new IllegalArgumentException(value);
        };
    }
}
