package az.yolcu.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;

class CommonTest {
    @Test
    void haversine() {
        // Elmlər → Nərimanov ≈ 5.6 km (düz xətt)
        assertEquals(5600, GeoUtil.distanceMeters(40.3755, 49.8156, 40.4029, 49.8711), 150);
        assertEquals(0, GeoUtil.distanceMeters(40.4, 49.8, 40.4, 49.8), 1e-9);
    }

    @Test
    void demoClockStartsAtConfiguredTime() {
        var real = Clock.fixed(Instant.parse("2026-10-17T15:00:00Z"), AppClock.ZONE);
        var clock = new AppClock(real, LocalTime.of(8, 12));
        assertEquals(LocalTime.of(8, 12), clock.now().toLocalTime());
        assertEquals(8.2, clock.hourOfDay(), 1e-9);
    }

    @Test
    void csvEscaping() {
        assertEquals("", Csv.field(null));
        assertEquals("abc", Csv.field("abc"));
        assertEquals("\"a;b\"", Csv.field("a;b"));
        assertEquals("\"a,b\"", Csv.field("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", Csv.field("say \"hi\""));
        assertEquals("\"l1\nl2\"", Csv.field("l1\nl2"));
        assertEquals("'=SUM(A1)", Csv.field("=SUM(A1)"));
        assertEquals("'+1", Csv.field("+1"));
        assertEquals("'-1", Csv.field("-1"));
        assertEquals("'@x", Csv.field("@x"));
        assertEquals("'\tx", Csv.field("\tx"));
        assertEquals("\"'\rx\"", Csv.field("\rx"));
        assertEquals("\"'=1;2\"", Csv.field("=1;2"));
        assertEquals("a;\"b;c\";\r\n", Csv.row("a", "b;c", null));
    }
}
