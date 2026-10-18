package az.yolcu.survey.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/** questionId → chosen option label. */
public record AnswerRequest(@NotEmpty Map<String, String> answers) {}
