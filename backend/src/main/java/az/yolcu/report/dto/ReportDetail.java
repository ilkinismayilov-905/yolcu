package az.yolcu.report.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.List;

/** ReportListItem fields + detail. `passenger` ("Gizli") only on admin endpoints. */
public record ReportDetail(String code, OffsetDateTime createdAt, String route, String bort, String operator,
                           String category, String textPreview, String status, boolean late, String place,
                           String text, boolean hasPhoto, int coinsGiven, String answerText, List<ReportEventDto> events,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String passenger) {
    public static ReportDetail of(ReportListItem i, String text, boolean hasPhoto, int coinsGiven, String answerText,
                                  List<ReportEventDto> events, String passenger) {
        return new ReportDetail(i.code(), i.createdAt(), i.route(), i.bort(), i.operator(), i.category(), i.textPreview(),
                i.status(), i.late(), i.place(), text, hasPhoto, coinsGiven, answerText, events, passenger);
    }
}
