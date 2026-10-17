package az.yolcu.auth.dto;

public record AuthResponse(String token, UserDto user) {}
