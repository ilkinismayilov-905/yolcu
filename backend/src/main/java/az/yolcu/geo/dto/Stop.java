package az.yolcu.geo.dto;

import java.util.List;

public record Stop(String id, String name, double lat, double lon, List<String> routes, String kind, boolean accessible) {}
