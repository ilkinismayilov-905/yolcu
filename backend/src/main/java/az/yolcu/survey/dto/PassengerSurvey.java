package az.yolcu.survey.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record PassengerSurvey(Long id, String title, String status, int coinReward, boolean answered,
                              List<Question> questions) {
    @Schema(name = "PassengerSurveyQuestion")
    public record Question(Long id, String text, List<String> options) {}
}
