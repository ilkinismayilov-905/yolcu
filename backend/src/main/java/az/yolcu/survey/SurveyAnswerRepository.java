package az.yolcu.survey;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SurveyAnswerRepository extends JpaRepository<SurveyAnswer, Long> {
    boolean existsBySurveyIdAndUserId(Long surveyId, Long userId);

    long countBySurveyId(Long surveyId);

    @Query("select a.answers from SurveyAnswer a where a.surveyId = :surveyId")
    List<String> answersOf(Long surveyId);

    /** [surveyId, count] rows. */
    @Query("select a.surveyId, count(a) from SurveyAnswer a where a.surveyId in :ids group by a.surveyId")
    List<Object[]> countBySurveyIds(Collection<Long> ids);
}
