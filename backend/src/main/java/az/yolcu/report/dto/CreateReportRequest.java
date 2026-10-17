package az.yolcu.report.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(
        @NotBlank @Size(max = 10) String route,
        @Size(max = 20) String bort,
        @NotBlank @Size(max = 40) String category,
        @Size(max = 2000) String text,
        Boolean hasPhoto,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lon) {}
