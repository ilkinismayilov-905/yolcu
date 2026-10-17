package az.yolcu.notification;

import az.yolcu.common.AppClock;
import az.yolcu.notification.dto.NotificationDto;
import az.yolcu.stream.StreamEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores a passenger notification and pushes `notification.created` to that user. */
@Service
public class NotificationService {
    private final NotificationRepository repo;
    private final ApplicationEventPublisher events;
    private final AppClock clock;

    public NotificationService(NotificationRepository repo, ApplicationEventPublisher events, AppClock clock) {
        this.repo = repo;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public NotificationDto create(long userId, String type, String title, String body, String target) {
        var dto = NotificationDto.of(repo.save(new Notification(userId, type, title, body, target, clock.now())));
        events.publishEvent(StreamEvent.toUser(userId, "notification.created", dto));
        return dto;
    }
}
