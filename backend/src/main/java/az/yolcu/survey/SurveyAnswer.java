package az.yolcu.survey;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "survey_answers")
public class SurveyAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long surveyId;
    private Long userId;
    private String answers; // JSON: {"<questionId>":"<option>"}
    private OffsetDateTime createdAt;

    protected SurveyAnswer() {}

    public SurveyAnswer(Long surveyId, Long userId, String answers, OffsetDateTime createdAt) {
        this.surveyId = surveyId;
        this.userId = userId;
        this.answers = answers;
        this.createdAt = createdAt;
    }

    public String getAnswers() { return answers; }
}
