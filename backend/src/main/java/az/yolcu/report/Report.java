package az.yolcu.report;

import az.yolcu.fleet.Operator;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "reports")
public class Report {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    private Long userId;
    private String route;
    private String bort;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "operator_id")
    private Operator operator;

    private String category;
    private String text;
    private boolean hasPhoto;
    private String place;
    private Double lat;
    private Double lon;

    @Enumerated(EnumType.STRING)
    private ReportStatus status;

    private String answerText;
    private OffsetDateTime createdAt;
    private OffsetDateTime forwardedAt;
    private OffsetDateTime updatedAt;

    protected Report() {}

    public Report(String code, Long userId, String route, String bort, Operator operator, String category, String text,
                  boolean hasPhoto, String place, Double lat, Double lon, OffsetDateTime now) {
        this.code = code;
        this.userId = userId;
        this.route = route;
        this.bort = bort;
        this.operator = operator;
        this.category = category;
        this.text = text;
        this.hasPhoto = hasPhoto;
        this.place = place;
        this.lat = lat;
        this.lon = lon;
        this.status = ReportStatus.NEW;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void moveTo(ReportStatus next, OffsetDateTime now) {
        if (next == ReportStatus.FORWARDED) forwardedAt = now;
        status = next;
        updatedAt = now;
    }

    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public Long getUserId() { return userId; }
    public String getRoute() { return route; }
    public String getBort() { return bort; }
    public Operator getOperator() { return operator; }
    public String getCategory() { return category; }
    public String getText() { return text; }
    public boolean isHasPhoto() { return hasPhoto; }
    public String getPlace() { return place; }
    public ReportStatus getStatus() { return status; }
    public String getAnswerText() { return answerText; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getForwardedAt() { return forwardedAt; }
}
