package az.yolcu.notification;

import az.yolcu.common.AppClock;
import az.yolcu.common.PageResponse;
import az.yolcu.notification.dto.NotificationDto;
import az.yolcu.stream.StreamEvent;
import java.util.Collection;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores a passenger notification and pushes `notification.created` to that user. */
@Service
public class NotificationService {
    private final NotificationRepository repo;
    private final ApplicationEventPublisher events;
    private final AppClock clock;
    private final JdbcTemplate jdbc;

    public NotificationService(NotificationRepository repo, ApplicationEventPublisher events, AppClock clock, JdbcTemplate jdbc) {
        this.repo = repo;
        this.events = events;
        this.clock = clock;
        this.jdbc = jdbc;
    }

    @Transactional
    public NotificationDto create(long userId, String type, String title, String body, String target) {
        var dto = NotificationDto.of(repo.save(new Notification(userId, type, title, body, target, clock.now())));
        events.publishEvent(StreamEvent.toUser(userId, "notification.created", dto));
        return dto;
    }

    /** Same notification for many users: one JDBC batch insert, then one select to get ids for the SSE payloads. */
    @Transactional
    public int createAll(Collection<Long> userIds, String type, String title, String body, String target) {
        if (userIds.isEmpty()) return 0;
        long before = repo.maxId();
        var now = clock.now();
        jdbc.batchUpdate("insert into notifications (user_id, type, title, body, target, created_at) values (?, ?, ?, ?, ?, ?)",
                userIds.stream().map(id -> new Object[]{id, type, title, body, target, now}).toList());
        var created = repo.findByTargetAndIdGreaterThan(target, before);
        for (var n : created) events.publishEvent(StreamEvent.toUser(n.getUserId(), "notification.created", NotificationDto.of(n)));
        return created.size();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> mine(long userId, int page, int size) {
        PageResponse.check(page, size);
        var sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        return PageResponse.of(repo.findByUserId(userId, PageRequest.of(page, size, sort)), NotificationDto::of);
    }
}
