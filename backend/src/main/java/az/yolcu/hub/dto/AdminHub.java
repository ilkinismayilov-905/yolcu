package az.yolcu.hub.dto;

/** GET /admin/hubs item (03_API §9 "Hub kameraları"): /live/hubs fields + camera data. */
public record AdminHub(String id, String name, double lat, double lon, int count, int capacity, int trend10, int forecast15,
                       String level, int avgWaitMin, String camera, String recommendation, String kind) {}
