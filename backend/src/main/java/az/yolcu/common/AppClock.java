package az.yolcu.common;

import az.yolcu.config.YolcuProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Demo clock: starts at yolcu.demo.clock-start today (Baku time) and runs at real speed. */
@Component
public class AppClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Baku");

    private final Clock clock;

    @Autowired
    public AppClock(YolcuProperties props) {
        this(Clock.system(ZONE), props.demo() != null ? props.demo().clockStart() : null);
    }

    AppClock(Clock real, LocalTime start) {
        if (start == null) {
            this.clock = real;
        } else {
            var startAt = LocalDate.now(real).atTime(start).atZone(ZONE).toInstant();
            this.clock = Clock.offset(real, Duration.between(real.instant(), startAt));
        }
    }

    public OffsetDateTime now() { return OffsetDateTime.now(clock); }

    /** Hour of day as decimal, e.g. 08:12 → 8.2 (used by the simulator's peak curves). */
    public double hourOfDay() {
        var t = now();
        return t.getHour() + t.getMinute() / 60.0 + t.getSecond() / 3600.0;
    }
}
