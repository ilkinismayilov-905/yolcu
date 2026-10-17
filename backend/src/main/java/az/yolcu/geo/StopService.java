package az.yolcu.geo;

import az.yolcu.common.GeoUtil;
import az.yolcu.config.YolcuProperties;
import az.yolcu.geo.dto.NearbyStop;
import az.yolcu.geo.dto.Place;
import az.yolcu.geo.dto.Stop;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Stops and places come from mocks/stops_seed.json and mocks/places.json (no stops table in the schema). */
@Service
public class StopService {
    private final List<Stop> stops;
    private final List<Place> places;

    public StopService(YolcuProperties props, ObjectMapper mapper) throws IOException {
        var reader = mapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var dir = props.mocksPath();
        this.stops = List.of(reader.convertValue(reader.readTree(dir.resolve("stops_seed.json").toFile()).get("items"), Stop[].class));
        this.places = List.of(reader.convertValue(reader.readTree(dir.resolve("places.json").toFile()).get("items"), Place[].class));
    }

    public List<Stop> all() { return stops; }

    public Optional<Stop> byId(String id) {
        return stops.stream().filter(s -> s.id().equals(id)).findFirst();
    }

    public List<NearbyStop> near(double lat, double lon, double radius) {
        return stops.stream()
                .map(s -> NearbyStop.of(s, GeoUtil.distanceMeters(lat, lon, s.lat(), s.lon())))
                .filter(n -> n.distanceMeters() <= radius)
                .sorted(Comparator.comparingInt(NearbyStop::distanceMeters))
                .toList();
    }

    public List<Place> places() { return places; }
}
