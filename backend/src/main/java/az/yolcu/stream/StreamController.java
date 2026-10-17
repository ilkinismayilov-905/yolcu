package az.yolcu.stream;

import az.yolcu.user.Role;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class StreamController {
    private final EventBus bus;

    public StreamController(EventBus bus) {
        this.bus = bus;
    }

    @GetMapping(value = "/api/v1/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth) throws IOException {
        long userId = (Long) auth.getPrincipal();
        var role = Role.valueOf(auth.getAuthorities().iterator().next().getAuthority().substring("ROLE_".length()));
        var emitter = new SseEmitter(0L);
        // first event so EventSource fires onopen/onmessage immediately
        emitter.send(SseEmitter.event().name("hello").data(Map.of("userId", userId, "role", role, "ts", Instant.now().toString())));
        return bus.register(userId, role, emitter);
    }
}
