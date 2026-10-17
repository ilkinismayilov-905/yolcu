package az.yolcu.geo;

import az.yolcu.common.ApiException;
import az.yolcu.config.YolcuProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** data/processed/*.geojson kept in memory; layer name = file name in kebab-case (bike_lanes → bike-lanes). */
@Component
public class GeoStore {
    static final String[] EXPECTED = {"terminals", "routes", "bike-parkings", "bike-lanes", "metro-exits"};
    private static final Logger log = LoggerFactory.getLogger(GeoStore.class);

    private final Map<String, JsonNode> layers = new ConcurrentHashMap<>();

    public GeoStore(YolcuProperties props, ObjectMapper mapper) throws IOException {
        var dir = props.dataPath();
        if (Files.isDirectory(dir)) {
            try (var files = Files.list(dir)) {
                for (var f : files.filter(p -> p.toString().endsWith(".geojson")).toList()) {
                    var name = f.getFileName().toString().replace(".geojson", "").replace('_', '-');
                    try {
                        layers.put(name, mapper.readTree(f.toFile()));
                    } catch (IOException e) {
                        log.warn("GeoStore: cannot parse {}: {}", f, e.getMessage());
                    }
                }
            }
        }
        for (var name : EXPECTED) {
            if (!layers.containsKey(name)) log.warn("GeoStore: layer '{}' missing in {}", name, dir.toAbsolutePath());
        }
        log.info("GeoStore: loaded layers {} from {}", layers.keySet(), dir.toAbsolutePath());
    }

    public Optional<JsonNode> find(String name) {
        return Optional.ofNullable(layers.get(name));
    }

    public JsonNode get(String name) {
        return find(name).orElseThrow(() -> ApiException.notFound("Qat tapılmadı: " + name));
    }
}
