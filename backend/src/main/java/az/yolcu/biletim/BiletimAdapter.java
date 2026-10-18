package az.yolcu.biletim;

import java.time.OffsetDateTime;
import java.util.List;

/** Source of intercity arrivals at Avtovağzal. Today a mock (SIM); later the Biletim.az API. */
public interface BiletimAdapter {
    record Arrival(OffsetDateTime at, String from, String plate, int ticketsSold, String status) {}

    /** Upcoming arrivals (at ≥ now), any order. */
    List<Arrival> arrivals();
}
