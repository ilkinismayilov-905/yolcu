package az.yolcu.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Liveness for Render: no DB call, so a slow/restarting DB does not get the container killed. */
@RestController
public class HealthController {
    public record Health(String status) {}

    private static final Health OK = new Health("ok");

    @GetMapping("/api/v1/health")
    public Health health() { return OK; }
}
