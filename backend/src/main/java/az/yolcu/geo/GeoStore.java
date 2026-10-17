package az.yolcu.geo;

import az.yolcu.common.ApiException;
import az.yolcu.config.YolcuProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * data/processed/*.geojson kept in memory; layer name = file name in kebab-case (bike_lanes → bike-lanes).
 * Each layer is serialized once at load; routes-lite is derived from routes (Douglas–Peucker).
 */
@Component
public class GeoStore {
    static final String[] EXPECTED = {"terminals", "routes", "bike-parkings", "bike-lanes", "metro-exits"};
    static final double LITE_TOLERANCE_DEG = 0.00008; // ≈ 8–9 m in Baku
    private static final Logger log = LoggerFactory.getLogger(GeoStore.class);

    public record Cached(byte[] body, String etag) {}

    private final Map<String, JsonNode> layers = new ConcurrentHashMap<>();
    private final Map<String, Cached> cached = new ConcurrentHashMap<>();
    private final Map<String, List<Polyline>> routeLines = new LinkedHashMap<>();

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
        find("routes").ifPresent(routes -> {
            layers.put("routes-lite", simplify(routes, mapper));
            for (var f : routes.path("features")) {
                routeLines.computeIfAbsent(f.path("properties").path("route").asText(), k -> new ArrayList<>())
                        .addAll(Polyline.parts(f.path("geometry")));
            }
        });
        for (var e : layers.entrySet()) {
            var body = mapper.writeValueAsBytes(e.getValue());
            // weak ETag: Tomcat does not gzip responses carrying a strong one
            cached.put(e.getKey(), new Cached(body, "W/\"" + Integer.toHexString(Arrays.hashCode(body)) + "-" + body.length + "\""));
        }
        log.info("GeoStore: loaded layers {} from {}", layers.keySet(), dir.toAbsolutePath());
    }

    public Optional<JsonNode> find(String name) {
        return Optional.ofNullable(layers.get(name));
    }

    public JsonNode get(String name) {
        return find(name).orElseThrow(() -> notFound(name));
    }

    /** Pre-serialized layer bytes + ETag (served as-is by GeoController). */
    public Cached bytes(String name) {
        var c = cached.get(name);
        if (c == null) throw notFound(name);
        return c;
    }

    /** Full-resolution trajectories keyed by properties.route (a route may have several parts/features). */
    public Map<String, List<Polyline>> routeLines() { return routeLines; }

    private static ApiException notFound(String name) { return ApiException.notFound("Qat tapılmadı: " + name); }

    /** routes → routes-lite: Douglas–Peucker per line, properties route/name only, coordinates rounded to 5 decimals. */
    static JsonNode simplify(JsonNode routes, ObjectMapper mapper) {
        var out = mapper.createObjectNode().put("type", "FeatureCollection");
        var features = out.putArray("features");
        for (var f : routes.path("features")) {
            var nf = features.addObject().put("type", "Feature");
            var props = nf.putObject("properties");
            props.set("route", f.path("properties").path("route"));
            props.set("name", f.path("properties").path("name"));
            var g = f.path("geometry");
            var ng = nf.putObject("geometry").put("type", g.path("type").asText());
            var coords = ng.putArray("coordinates");
            if ("MultiLineString".equals(g.path("type").asText())) {
                for (var line : g.path("coordinates")) simplifyLine(line, coords.addArray());
            } else {
                simplifyLine(g.path("coordinates"), coords);
            }
        }
        return out;
    }

    private static void simplifyLine(JsonNode line, ArrayNode target) {
        int n = line.size();
        double[] x = new double[n], y = new double[n];
        for (int i = 0; i < n; i++) {
            x[i] = line.get(i).get(0).asDouble();
            y[i] = line.get(i).get(1).asDouble();
        }
        boolean[] keep = douglasPeucker(x, y, LITE_TOLERANCE_DEG);
        for (int i = 0; i < n; i++) {
            if (keep[i]) target.addArray().add(round5(x[i])).add(round5(y[i]));
        }
    }

    /** Iterative Douglas–Peucker in degree space; endpoints always kept. */
    static boolean[] douglasPeucker(double[] x, double[] y, double tol) {
        int n = x.length;
        var keep = new boolean[n];
        if (n == 0) return keep;
        keep[0] = keep[n - 1] = true;
        var stack = new ArrayList<int[]>();
        stack.add(new int[]{0, n - 1});
        while (!stack.isEmpty()) {
            var r = stack.remove(stack.size() - 1);
            int a = r[0], b = r[1], idx = -1;
            double max = tol;
            for (int i = a + 1; i < b; i++) {
                double d = segmentDistance(x[i], y[i], x[a], y[a], x[b], y[b]);
                if (d > max) {
                    max = d;
                    idx = i;
                }
            }
            if (idx >= 0) {
                keep[idx] = true;
                stack.add(new int[]{a, idx});
                stack.add(new int[]{idx, b});
            }
        }
        return keep;
    }

    private static double segmentDistance(double px, double py, double ax, double ay, double bx, double by) {
        double dx = bx - ax, dy = by - ay, l2 = dx * dx + dy * dy;
        double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / l2));
        return Math.hypot(px - ax - t * dx, py - ay - t * dy);
    }

    private static double round5(double v) { return Math.round(v * 1e5) / 1e5; }
}
