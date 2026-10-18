package az.yolcu.biletim;

import az.yolcu.common.AppClock;
import az.yolcu.config.YolcuProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * SIM (03_API §9 "Avtovağzal və Biletim.az"): mocks/biletim_arrivals.json, each minutesFromNow is added to the demo clock
 * at boot; an arrival already in the past moves forward by whole 180-minute cycles, so it reappears at the end of the list.
 */
@Component
public class MockBiletimAdapter implements BiletimAdapter {
    static final Duration CYCLE = Duration.ofMinutes(180);

    private record Item(int minutesFromNow, String from, String plate, int ticketsSold, String status) {}

    private final AppClock clock;
    private final OffsetDateTime anchor;
    private final List<Item> items = new ArrayList<>();

    public MockBiletimAdapter(YolcuProperties props, ObjectMapper mapper, AppClock clock) throws IOException {
        this.clock = clock;
        this.anchor = clock.now().truncatedTo(ChronoUnit.MINUTES);
        for (var n : mapper.readTree(props.mocksPath().resolve("biletim_arrivals.json").toFile()).path("items")) {
            items.add(new Item(n.path("minutesFromNow").asInt(), n.path("from").asText(), n.path("plate").asText(),
                    n.path("ticketsSold").asInt(), n.path("status").asText()));
        }
    }

    @Override
    public List<Arrival> arrivals() {
        var now = clock.now();
        return items.stream().map(i -> {
            var at = anchor.plusMinutes(i.minutesFromNow());
            if (at.isBefore(now)) {
                long cycles = Duration.between(at, now).toMinutes() / CYCLE.toMinutes() + 1;
                at = at.plus(CYCLE.multipliedBy(cycles));
            }
            return new Arrival(at, i.from(), i.plate(), i.ticketsSold(), i.status());
        }).toList();
    }
}
