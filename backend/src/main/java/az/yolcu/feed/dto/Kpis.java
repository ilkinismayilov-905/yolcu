package az.yolcu.feed.dto;

/** GET /admin/kpis (03_API_CONTRACT §9). */
public record Kpis(int avgSpeedKmh, int avgSpeedYesterday, int waitingAtHubs, int avgOccupancy, int overfullBuses,
                   double satisfaction, double satisfactionWeekDelta) {}
