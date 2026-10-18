package az.yolcu.survey.dto;

import java.util.List;

public record PassengerSurvey(Long id, String title, String status, int coinReward, boolean answered,
                              List<Question> questions) {
    public record Question(Long id, String text, List<String> options) {}
}
