package az.yolcu.survey.dto;

import java.util.List;

public record SurveyResults(Long id, long responses, List<Question> questions) {
    public record Question(Long id, String text, long total, List<Option> options) {}

    public record Option(String label, long count, int percent) {}
}
