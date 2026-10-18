package az.yolcu.survey;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SurveyQuestionRepository extends JpaRepository<SurveyQuestion, Long> {
    List<SurveyQuestion> findBySurveyIdOrderByOrdAsc(Long surveyId);
}
