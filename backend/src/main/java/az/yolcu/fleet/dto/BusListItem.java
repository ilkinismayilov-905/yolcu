package az.yolcu.fleet.dto;

/** GET /admin/buses item. occupancy = percent from the simulator (null if the bus is not simulated). */
public record BusListItem(String bort, String operator, String operatorCode, String route, String type, String model,
                          int capacity, String status, Integer occupancy, Double driverRating) {}
