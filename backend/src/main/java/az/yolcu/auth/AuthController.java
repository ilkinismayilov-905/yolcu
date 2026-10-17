package az.yolcu.auth;

import az.yolcu.auth.dto.AuthResponse;
import az.yolcu.auth.dto.LoginRequest;
import az.yolcu.auth.dto.UserDto;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @PostMapping("/auth/demo-login")
    public AuthResponse demoLogin() {
        return auth.demoLogin();
    }

    @GetMapping("/me")
    public UserDto me(@AuthenticationPrincipal Long userId) {
        return auth.me(userId);
    }
}
