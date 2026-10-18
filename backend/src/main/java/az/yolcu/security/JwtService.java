package az.yolcu.security;

import az.yolcu.config.YolcuProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final Duration TTL = Duration.ofHours(12);

    private final SecretKey key;

    public JwtService(YolcuProperties props) {
        var secret = props.jwtSecret() == null ? new byte[0] : props.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) throw new IllegalStateException(
                "JWT_SECRET (yolcu.jwt-secret) is missing or shorter than 32 bytes; set a random 32+ character secret");
        this.key = Keys.hmacShaKeyFor(secret);
    }

    public String issue(long userId, String role) {
        var now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(TTL)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** Empty when the token is missing, expired or forged. */
    public Optional<Claims> parse(String token) {
        try {
            return Optional.of(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
