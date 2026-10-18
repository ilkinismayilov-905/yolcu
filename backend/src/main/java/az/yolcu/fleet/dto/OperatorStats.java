package az.yolcu.fleet.dto;

import java.util.List;

/** GET /admin/operators item. */
public record OperatorStats(long id, String code, String name, int fleetTotal, int onLine, Double avgRating,
                            double complaintsPer10k, int punctuality, List<String> routes) {}
