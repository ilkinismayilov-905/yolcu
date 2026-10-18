package az.yolcu.analytics.dto;

import java.util.List;

/** matrix[from][to] = trips, same order as zones. */
public record OdMatrix(List<String> zones, long[][] matrix) {}
