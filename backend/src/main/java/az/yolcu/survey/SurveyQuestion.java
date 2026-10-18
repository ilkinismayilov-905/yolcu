package az.yolcu.survey;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "survey_questions")
public class SurveyQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long surveyId;
    private int ord;
    private String text;
    private String options;    // JSON array of labels
    private String seedCounts; // JSON array of historic counts, same order as options; null for new surveys

    protected SurveyQuestion() {}

    public SurveyQuestion(Long surveyId, int ord, String text, String options) {
        this.surveyId = surveyId;
        this.ord = ord;
        this.text = text;
        this.options = options;
    }

    public Long getId() { return id; }
    public Long getSurveyId() { return surveyId; }
    public int getOrd() { return ord; }
    public String getText() { return text; }
    public String getOptions() { return options; }
    public String getSeedCounts() { return seedCounts; }
}
