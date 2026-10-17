package az.yolcu.sim.dto;

/** GET /live/hubs item (03_API_CONTRACT §8). */
public record LiveHub(String id, String name, double lat, double lon, int count, int capacity, int trend10, int forecast15) {}
