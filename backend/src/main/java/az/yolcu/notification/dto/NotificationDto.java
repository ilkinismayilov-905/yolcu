package az.yolcu.notification.dto;

import az.yolcu.common.AppClock;
import az.yolcu.notification.Notification;
import java.time.OffsetDateTime;

public record NotificationDto(Long id, String type, String title, String body, String target, boolean read,
                              OffsetDateTime createdAt) {
    public static NotificationDto of(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getBody(), n.getTarget(), n.getReadAt() != null,
                AppClock.local(n.getCreatedAt()));
    }
}
