package az.yolcu.geo.dto;

import java.util.List;

/** Stop from 03_API §2 plus distanceMeters from the query point. */
public record NearbyStop(String id, String name, double lat, double lon, List<String> routes, String kind,
                         boolean accessible, int distanceMeters) {
    public static NearbyStop of(Stop s, double distance) {
        return new NearbyStop(s.id(), s.name(), s.lat(), s.lon(), s.routes(), s.kind(), s.accessible(),
                (int) Math.round(distance));
    }
}
