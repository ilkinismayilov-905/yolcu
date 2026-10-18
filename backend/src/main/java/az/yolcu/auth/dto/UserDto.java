package az.yolcu.auth.dto;

import az.yolcu.user.User;

public record UserDto(Long id, String fullName, String email, String role, int coinBalance, boolean accessibility,
                      boolean trackingConsent) {
    public static UserDto of(User u) {
        return new UserDto(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(), u.getCoinBalance(), u.isAccessibility(),
                u.isTrackingConsent());
    }
}
