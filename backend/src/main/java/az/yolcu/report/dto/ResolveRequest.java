package az.yolcu.report.dto;

import jakarta.validation.constraints.Size;

public record ResolveRequest(@Size(max = 2000) String answerText) {}
