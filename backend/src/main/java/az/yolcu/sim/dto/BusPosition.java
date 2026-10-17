package az.yolcu.sim.dto;

/** bus.positions SSE item and GET /live/buses item; occupancy is percent (0–100), capacity is places. */
public record BusPosition(String bort, String route, double lat, double lon, int bearing, int occupancy, int capacity,
                          String type) {}
