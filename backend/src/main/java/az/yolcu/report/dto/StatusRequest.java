package az.yolcu.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record StatusRequest(@NotNull String status, @Size(max = 500) String note) {}
