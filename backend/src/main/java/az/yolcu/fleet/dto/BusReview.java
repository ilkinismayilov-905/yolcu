package az.yolcu.fleet.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record BusReview(int driverRating, int roadRating, int busRating, String crowdLevel, List<String> tags, String comment,
                        OffsetDateTime createdAt) {}
