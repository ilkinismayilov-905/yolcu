package az.yolcu.survey.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record SurveyResults(Long id, long responses, List<Question> questions) {
    @Schema(name = "SurveyResultQuestion")
    public record Question(Long id, String text, long total, List<Option> options) {}

    @Schema(name = "SurveyResultOption")
    public record Option(String label, long count, int percent) {}
}
