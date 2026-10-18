package az.yolcu.proposal.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** GET /admin/hub-proposals item and proposal.created SSE payload (03_API_CONTRACT §9). */
public record HubProposal(long id, String name, double lat, double lon, int jamMinutes, int routesCount, int metroM,
                          int waiting, int paxPerHour, int confidence, List<String> items, Impact impact, String status,
                          String reason, OffsetDateTime createdAt) {

    public record Impact(int transferMin, int toMetroPerDay, int waitingPct, String costAzn) {}

    public static String reason(int metroM) {
        return "Bu nöqtədə tıxac sərnişinləri dayanacaqda saxlayır, amma " + metroM + " m-də metro var. Hub yaradılarsa, "
                + "avtobusdan metroya və skuterə keçid rahatlaşır və tıxacdakı gözləmə azalır.";
    }
}
