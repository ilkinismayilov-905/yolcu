package az.yolcu.occupancy.dto;

/** GET /admin/occupancy/routes/{route}/buses item; occupancy is people on board (not %), capacity is places. */
public record OccupancyBus(String bort, String type, String near, int occupancy, int capacity) {}
