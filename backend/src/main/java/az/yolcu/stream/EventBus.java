package az.yolcu.stream;

import az.yolcu.user.Role;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Registry of open SSE connections. Domain code should publish StreamEvent instead of calling this directly. */
@Component
public class EventBus {
    private static final Logger log = LoggerFactory.getLogger(EventBus.class);

    record Client(long userId, Role role, SseEmitter emitter) {}

    private final List<Client> clients = new CopyOnWriteArrayList<>();

    public SseEmitter register(long userId, Role role, SseEmitter emitter) {
        var client = new Client(userId, role, emitter);
        clients.add(client);
        emitter.onCompletion(() -> remove(client, "completed"));
        emitter.onTimeout(() -> remove(client, "timeout"));
        emitter.onError(e -> remove(client, "error: " + e.getMessage()));
        log.info("SSE connected user={} role={} (open={})", userId, role, clients.size());
        return emitter;
    }

    public void publishAll(String event, Object data) { send(c -> true, event, data); }

    public void publishToRole(Role role, String event, Object data) { send(c -> c.role == role, event, data); }

    public void publishToUser(long userId, String event, Object data) { send(c -> c.userId == userId, event, data); }

    public int size() { return clients.size(); }

    @Scheduled(fixedRate = 20_000, initialDelay = 20_000)
    void ping() { publishAll("ping", Map.of("ts", Instant.now().toString())); }

    private void send(Predicate<Client> filter, String event, Object data) {
        for (var c : clients) {
            if (!filter.test(c)) continue;
            try {
                c.emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException | IllegalStateException e) {
                remove(c, "send failed: " + e.getMessage());
                c.emitter.completeWithError(e);
            }
        }
    }

    private void remove(Client c, String reason) {
        if (clients.remove(c)) log.info("SSE removed user={} role={} reason={} (open={})", c.userId, c.role, reason, clients.size());
    }
}
