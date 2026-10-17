package az.yolcu.report.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ShareRequest(@NotEmpty List<String> targets, Boolean hidePersonal) {}
