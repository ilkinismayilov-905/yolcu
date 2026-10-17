package az.yolcu.report;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "report_events")
public class ReportEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long reportId;
    private String type;   // CREATED | STATUS | FORWARDED | SHARED | ANSWERED
    private String note;
    private String actor;
    private OffsetDateTime createdAt;

    protected ReportEvent() {}

    public ReportEvent(Long reportId, String type, String note, String actor, OffsetDateTime createdAt) {
        this.reportId = reportId;
        this.type = type;
        this.note = note;
        this.actor = actor;
        this.createdAt = createdAt;
    }

    public String getType() { return type; }
    public String getNote() { return note; }
    public String getActor() { return actor; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
