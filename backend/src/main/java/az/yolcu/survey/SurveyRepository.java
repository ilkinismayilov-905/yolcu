package az.yolcu.survey;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SurveyRepository extends JpaRepository<Survey, Long> {
    Page<Survey> findByStatus(String status, Pageable pageable);

    @Query(value = "select id from users where role = 'PASSENGER' and synthetic = false order by id", nativeQuery = true)
    List<Long> allPassengers();

    @Query(value = "select id from users u where role = 'PASSENGER' and synthetic = false and exists "
            + "(select 1 from trips t where t.user_id = u.id and t.route in (:routes)) order by id", nativeQuery = true)
    List<Long> passengersOnRoutes(List<String> routes);

    @Query(value = "select id from users u where role = 'PASSENGER' and synthetic = false and exists "
            + "(select 1 from trips t where t.user_id = u.id and t.alight_lat between :latMin and :latMax "
            + "and t.alight_lon between :lonMin and :lonMax) order by id", nativeQuery = true)
    List<Long> passengersAlightingIn(double latMin, double latMax, double lonMin, double lonMax);

    @Query(value = "select id from users u where synthetic = true and not exists "
            + "(select 1 from survey_answers a where a.survey_id = :surveyId and a.user_id = u.id) order by id limit :n",
            nativeQuery = true)
    List<Long> syntheticWithoutAnswer(long surveyId, int n);
}
