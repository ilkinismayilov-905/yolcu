package az.yolcu.occupancy.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record IntervalRequest(@NotNull @Min(value = 6, message = "6–16 dəqiqə arasında olmalıdır")
                              @Max(value = 16, message = "6–16 dəqiqə arasında olmalıdır") Integer intervalMin) {}
