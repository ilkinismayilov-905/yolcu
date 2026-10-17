package az.yolcu.feed;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "alerts")
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String category;
    private String title;
    private String body;
    private String target;
    private OffsetDateTime createdAt;

    protected Alert() {}

    public Alert(String category, String title, String body, String target, OffsetDateTime createdAt) {
        this.category = category;
        this.title = title;
        this.body = body;
        this.target = target;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getCategory() { return category; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getTarget() { return target; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
