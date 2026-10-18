package az.yolcu.survey;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "surveys")
public class Survey {
    public static final String DRAFT = "DRAFT", ACTIVE = "ACTIVE", CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String title;
    private String status;
    private String audience;
    private int coinReward;
    private OffsetDateTime createdAt;
    private OffsetDateTime publishedAt;
    private OffsetDateTime closedAt;
    private int seedResponses;

    protected Survey() {}

    public Survey(String title, String audience, int coinReward, OffsetDateTime now) {
        this.title = title;
        this.status = DRAFT;
        this.audience = audience;
        this.coinReward = coinReward;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getAudience() { return audience; }
    public int getCoinReward() { return coinReward; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getPublishedAt() { return publishedAt; }
    public OffsetDateTime getClosedAt() { return closedAt; }
    public int getSeedResponses() { return seedResponses; }

    public void publish(OffsetDateTime now) { status = ACTIVE; publishedAt = now; }
    public void close(OffsetDateTime now) { status = CLOSED; closedAt = now; }
}
