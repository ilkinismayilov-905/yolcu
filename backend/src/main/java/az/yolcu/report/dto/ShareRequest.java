package az.yolcu.report.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record ShareRequest(
        @NotEmpty List<@NotNull @Pattern(regexp = "OPERATOR|YPX|CITY", message = "OPERATOR | YPX | CITY") String> targets,
        Boolean hidePersonal) {}
