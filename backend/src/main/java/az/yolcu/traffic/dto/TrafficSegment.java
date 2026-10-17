package az.yolcu.traffic.dto;

import java.util.List;

/** GET /live/traffic item and traffic.updated SSE item; line = [[lat, lon], ...] along the real street. */
public record TrafficSegment(String id, String name, String level, int speedKmh, List<List<Double>> line) {}
