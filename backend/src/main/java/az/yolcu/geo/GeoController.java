package az.yolcu.geo;

import az.yolcu.geo.dto.NearbyStop;
import az.yolcu.geo.dto.Place;
import java.time.Duration;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GeoController {
    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    private final GeoStore geo;
    private final StopService stops;

    public GeoController(GeoStore geo, StopService stops) {
        this.geo = geo;
        this.stops = stops;
    }

    /** Pre-serialized bytes; If-None-Match with the ETag → 304 (handled by Spring for ResponseEntity). */
    @GetMapping("/geo/layers/{name}")
    public ResponseEntity<byte[]> layer(@PathVariable String name) {
        var layer = geo.bytes(name);
        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .eTag(layer.etag())
                .body(layer.body());
    }

    @GetMapping("/stops")
    public List<NearbyStop> stops(@RequestParam double lat, @RequestParam double lon,
                                  @RequestParam(defaultValue = "600") double radius) {
        return stops.near(lat, lon, radius);
    }

    @GetMapping("/places")
    public List<Place> places() {
        return stops.places();
    }
}
