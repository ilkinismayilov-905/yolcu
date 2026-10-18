package az.yolcu.auth;

import az.yolcu.auth.dto.AuthResponse;
import az.yolcu.auth.dto.ChangePasswordRequest;
import az.yolcu.auth.dto.LoginRequest;
import az.yolcu.auth.dto.UserDto;
import az.yolcu.common.ApiException;
import az.yolcu.security.JwtService;
import az.yolcu.user.Role;
import az.yolcu.user.User;
import az.yolcu.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public AuthResponse login(LoginRequest req) {
        var user = users.findByEmailIgnoreCase(req.email())
                .filter(u -> u.getPasswordHash() != null && encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("Email və ya şifrə yanlışdır"));
        return issue(user);
    }

    public AuthResponse demoLogin() {
        var user = users.findFirstByRoleOrderByIdAsc(Role.PASSENGER)
                .orElseThrow(() -> ApiException.notFound("Demo istifadəçi tapılmadı"));
        return issue(user);
    }

    public UserDto me(long userId) {
        return users.findById(userId).map(UserDto::of)
                .orElseThrow(() -> ApiException.unauthorized("İstifadəçi tapılmadı"));
    }

    @Transactional
    public void changePassword(long userId, ChangePasswordRequest req) {
        var user = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("İstifadəçi tapılmadı"));
        if (user.getPasswordHash() == null || !encoder.matches(req.currentPassword(), user.getPasswordHash()))
            throw ApiException.validation("Hazırkı şifrə yanlışdır");
        if (req.newPassword().equals(req.currentPassword()))
            throw ApiException.validation("Yeni şifrə hazırkı ilə eyni ola bilməz");
        user.setPasswordHash(encoder.encode(req.newPassword()));
    }

    private AuthResponse issue(User u) {
        return new AuthResponse(jwt.issue(u.getId(), u.getRole().name()), UserDto.of(u));
    }
}
