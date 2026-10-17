package az.yolcu.notification;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String type;
    private String title;
    private String body;
    private String target;
    private OffsetDateTime readAt;
    private OffsetDateTime createdAt;

    protected Notification() {}

    public Notification(Long userId, String type, String title, String body, String target, OffsetDateTime createdAt) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.body = body;
        this.target = target;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getTarget() { return target; }
    public OffsetDateTime getReadAt() { return readAt; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
