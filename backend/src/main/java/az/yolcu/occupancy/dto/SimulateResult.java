package az.yolcu.occupancy.dto;

/** GET /admin/occupancy/simulate: occupancy %, sat = expected satisfaction (1 decimal). */
public record SimulateResult(int occupancy, double waitMin, int busesNeeded, double sat, String note) {}
