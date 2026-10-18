package az.yolcu.fleet.dto;

import az.yolcu.report.dto.ReportListItem;
import java.util.List;

/** GET /admin/buses/{bort}: list item fields + last 5 reports + last 5 reviews (newest first). */
public record BusDetail(String bort, String operator, String operatorCode, String route, String type, String model,
                        int capacity, String status, Integer occupancy, Double driverRating, int reviewCount,
                        List<ReportListItem> reports, List<BusReview> reviews) {
    public static BusDetail of(BusListItem b, int reviewCount, List<ReportListItem> reports, List<BusReview> reviews) {
        return new BusDetail(b.bort(), b.operator(), b.operatorCode(), b.route(), b.type(), b.model(), b.capacity(),
                b.status(), b.occupancy(), b.driverRating(), reviewCount, reports, reviews);
    }
}
