package az.yolcu.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.user.Role;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class EventBusTest {
    @Autowired MockMvc mvc;
    @Autowired EventBus bus;
    @Autowired ApplicationEventPublisher publisher;

    static class Capture extends SseEmitter {
        final List<String> events = new ArrayList<>();
        boolean fail;

        @Override
        public void send(SseEventBuilder b) throws IOException {
            if (fail) throw new IOException("broken pipe");
            var sb = new StringBuilder();
            b.build().forEach(d -> sb.append(d.getData()));
            events.add(sb.toString());
        }
    }

    @Test
    void routesEventsAndDropsBrokenEmitters() {
        var a = new Capture();
        var b = new Capture();
        var c = new Capture();
        bus.register(1, Role.PASSENGER, a);
        bus.register(2, Role.DISPATCHER, b);
        bus.register(3, Role.DISPATCHER, c);
        int before = bus.size();

        publisher.publishEvent(StreamEvent.all("bus.positions", List.of()));
        assertThat(a.events).hasSize(1);
        assertThat(b.events).hasSize(1);
        assertThat(a.events.get(0)).contains("event:bus.positions");

        bus.publishToRole(Role.DISPATCHER, "report.created", Map.of("code", "A-1"));
        bus.publishToUser(1, "coin.updated", Map.of("balance", 5));
        assertThat(a.events).hasSize(2);
        assertThat(b.events).hasSize(2);

        c.fail = true;
        bus.publishAll("ping", Map.of());
        assertThat(bus.size()).isEqualTo(before - 1);
    }

    @Test
    void streamWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/stream"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
