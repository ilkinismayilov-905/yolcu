package az.yolcu.survey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateSurveyRequest(
        @NotBlank @Size(max = 160) String title,
        @NotNull @Pattern(regexp = "ALL|AVTOVAGZAL_2KM|ROUTE_88_4") String audience,
        @NotNull Integer coinReward,
        @NotEmpty @Size(max = 10) List<@Valid @NotNull Question> questions,
        boolean publish) {

    public record Question(@NotBlank @Size(max = 500) String text,
                           @NotNull @Size(min = 2, max = 10) List<@NotBlank @Size(max = 80) String> options) {}
}
