package az.yolcu.feed.dto;

import az.yolcu.common.AppClock;
import az.yolcu.feed.Alert;
import java.time.OffsetDateTime;

/** Alert (03_API_CONTRACT §9): GET /admin/feed item and alert.created SSE payload. */
public record AlertDto(Long id, String category, String title, String body, String target, OffsetDateTime createdAt) {
    public static AlertDto of(Alert a) {
        return new AlertDto(a.getId(), a.getCategory(), a.getTitle(), a.getBody(), a.getTarget(), AppClock.local(a.getCreatedAt()));
    }
}
