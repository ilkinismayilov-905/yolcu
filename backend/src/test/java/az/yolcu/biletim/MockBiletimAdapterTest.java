package az.yolcu.biletim;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import az.yolcu.common.AppClock;
import az.yolcu.config.YolcuProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Comparator;
import org.junit.jupiter.api.Test;

class MockBiletimAdapterTest {
    @Test
    void minutesFromNowAddedToClockAndPastRollsOverBy180() throws Exception {
        var props = new YolcuProperties(null, null, "../data", "../mocks", 0, null);
        var clock = mock(AppClock.class);
        var boot = OffsetDateTime.parse("2026-10-18T08:00:30+04:00");
        when(clock.now()).thenReturn(boot);
        var adapter = new MockBiletimAdapter(props, new ObjectMapper(), clock);

        var first = adapter.arrivals();
        assertThat(first).hasSize(7);
        assertThat(first.get(0).from()).isEqualTo("Gəncə");
        assertThat(first.get(0).at()).isEqualTo(OffsetDateTime.parse("2026-10-18T08:08:00+04:00")); // anchor truncated to minutes + 8

        when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-18T08:30:00+04:00")); // Gəncə 08:08, Şəki 08:23 passed
        var later = adapter.arrivals().stream().sorted(Comparator.comparing(BiletimAdapter.Arrival::at)).toList();
        assertThat(later.get(0).from()).isEqualTo("Lənkəran");
        assertThat(later.get(5).from()).isEqualTo("Gəncə");
        assertThat(later.get(5).at()).isEqualTo(OffsetDateTime.parse("2026-10-18T11:08:00+04:00"));
        assertThat(later.get(6).from()).isEqualTo("Şəki");
        assertThat(later.get(6).at()).isEqualTo(OffsetDateTime.parse("2026-10-18T11:23:00+04:00"));

        when(clock.now()).thenReturn(OffsetDateTime.parse("2026-10-18T14:30:00+04:00")); // two cycles later
        assertThat(adapter.arrivals()).allMatch(a -> !a.at().isBefore(clock.now()) && a.at().isBefore(clock.now().plusMinutes(180)));
    }
}
