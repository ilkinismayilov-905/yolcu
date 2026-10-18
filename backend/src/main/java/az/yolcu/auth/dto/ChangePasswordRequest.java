package az.yolcu.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 6, max = 100, message = "Yeni şifrə ən azı 6, ən çox 100 simvol olmalıdır") String newPassword) {}
