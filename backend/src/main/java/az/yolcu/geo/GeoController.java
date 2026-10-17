package az.yolcu.geo;

import az.yolcu.geo.dto.NearbyStop;
import az.yolcu.geo.dto.Place;
import com.fasterxml.jackson.databind.JsonNode;
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

    @GetMapping("/geo/layers/{name}")
    public ResponseEntity<JsonNode> layer(@PathVariable String name) {
        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)))
                .body(geo.get(name));
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
