package az.yolcu.occupancy.dto;

import jakarta.validation.constraints.NotNull;

public record AutoRequest(@NotNull Boolean enabled) {}
