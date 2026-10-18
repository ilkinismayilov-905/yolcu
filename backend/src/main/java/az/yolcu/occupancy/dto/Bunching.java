package az.yolcu.occupancy.dto;

/** GET /admin/occupancy/bunching item; occupancy in BusRef is people on board. */
public record Bunching(String route, BusRef front, BusRef back, int gapMin, String near) {
    public record BusRef(String bort, int occupancy) {}
}
