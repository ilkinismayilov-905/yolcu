package az.yolcu.occupancy.dto;

/** 03_API §9 GET /admin/occupancy/routes item and route.updated SSE payload. */
public record RouteOccupancy(String route, String type, String operator, int busCount, int avgOccupancy, int intervalMin,
                             int recommendedMin, double satNow, double satAfter, boolean autoMode) {}
