package az.yolcu.biletim.dto;

/** GET /admin/biletim/arrivals item (03_API §9). time = "HH:mm" Baku time. */
public record BiletimArrival(String time, String from, String plate, int ticketsSold, String status, int nextCityBusMin,
                             String level) {}
