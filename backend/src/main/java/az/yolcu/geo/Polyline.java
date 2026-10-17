package az.yolcu.geo;

import az.yolcu.common.GeoUtil;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Lat/lon polyline with cumulative arc length in meters. A closed loop (first ≈ last point) wraps around. */
public final class Polyline {
    private static final double M_PER_DEG_LAT = 110_540, M_PER_DEG_LON_EQ = 111_320;

    private final double[] lat, lon, cum;
    private final boolean closed;

    /** points: {lat, lon} */
    public Polyline(List<double[]> points) {
        int n = points.size();
        if (n < 2) throw new IllegalArgumentException("polyline needs ≥ 2 points");
        lat = new double[n];
        lon = new double[n];
        cum = new double[n];
        for (int i = 0; i < n; i++) {
            lat[i] = points.get(i)[0];
            lon[i] = points.get(i)[1];
            if (i > 0) cum[i] = cum[i - 1] + GeoUtil.distanceMeters(lat[i - 1], lon[i - 1], lat[i], lon[i]);
        }
        closed = GeoUtil.distanceMeters(lat[0], lon[0], lat[n - 1], lon[n - 1]) < 50;
    }

    /** GeoJSON LineString / MultiLineString ([lon, lat] coordinates) → one polyline per part. */
    public static List<Polyline> parts(JsonNode geometry) {
        var coords = geometry.path("coordinates");
        Iterable<JsonNode> lines = "MultiLineString".equals(geometry.path("type").asText()) ? coords : List.of(coords);
        var out = new ArrayList<Polyline>();
        for (JsonNode line : lines) {
            var pts = new ArrayList<double[]>();
            for (JsonNode c : line) pts.add(new double[]{c.get(1).asDouble(), c.get(0).asDouble()});
            if (pts.size() >= 2) out.add(new Polyline(pts));
        }
        return out;
    }

    public double length() { return cum[cum.length - 1]; }

    public boolean closed() { return closed; }

    public int size() { return lat.length; }

    public Polyline reversed() {
        var pts = new ArrayList<double[]>();
        for (int i = lat.length - 1; i >= 0; i--) pts.add(new double[]{lat[i], lon[i]});
        return new Polyline(pts);
    }

    public double wrap(double s) {
        double l = length();
        return l == 0 ? 0 : ((s % l) + l) % l;
    }

    /** Point at arc length s (wrapped): {lat, lon, bearingDeg}. */
    public double[] at(double s) {
        s = wrap(s);
        int i = Arrays.binarySearch(cum, s);
        if (i < 0) i = -i - 2;
        i = Math.max(0, Math.min(i, lat.length - 2));
        double seg = cum[i + 1] - cum[i], t = seg == 0 ? 0 : (s - cum[i]) / seg;
        return new double[]{lat[i] + t * (lat[i + 1] - lat[i]), lon[i] + t * (lon[i + 1] - lon[i]), bearing(i)};
    }

    /** Every pass of the line within maxM of the point, as {distanceM, s} at the closest spot of that pass. */
    public List<double[]> passes(double plat, double plon, double maxM) {
        double kx = Math.cos(Math.toRadians(plat)) * M_PER_DEG_LON_EQ, ky = M_PER_DEG_LAT;
        var out = new ArrayList<double[]>();
        double[] run = null;
        for (int i = 0; i + 1 < lat.length; i++) {
            double ax = (lon[i] - plon) * kx, ay = (lat[i] - plat) * ky;
            double dx = (lon[i + 1] - lon[i]) * kx, dy = (lat[i + 1] - lat[i]) * ky, l2 = dx * dx + dy * dy;
            double t = l2 == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / l2));
            double d = Math.hypot(ax + t * dx, ay + t * dy);
            if (d <= maxM) {
                if (run == null || d < run[0]) run = new double[]{d, cum[i] + t * (cum[i + 1] - cum[i])};
            } else if (run != null) {
                out.add(run);
                run = null;
            }
        }
        if (run != null) out.add(run);
        return out;
    }

    /** Closest point of the whole line: {distanceM, s}. */
    public double[] nearest(double plat, double plon) {
        return passes(plat, plon, Double.POSITIVE_INFINITY).get(0);
    }

    /** Sub-polyline from s0 forward to s1 ({lat, lon} points); wraps past the end only on a closed loop. */
    public List<double[]> slice(double s0, double s1) {
        double end = s1 >= s0 ? s1 : s1 + length();
        var pts = new ArrayList<double[]>();
        var a = at(s0);
        pts.add(new double[]{a[0], a[1]});
        outer:
        for (int lap = 0; lap < 2; lap++) {
            for (int i = 0; i < lat.length; i++) {
                double s = cum[i] + lap * length();
                if (s <= s0) continue;
                if (s >= end) break outer;
                var last = pts.get(pts.size() - 1);
                if (last[0] != lat[i] || last[1] != lon[i]) pts.add(new double[]{lat[i], lon[i]});
            }
        }
        var b = at(s1);
        pts.add(new double[]{b[0], b[1]});
        return pts;
    }

    /** Initial bearing of the segment starting at vertex i (skips zero-length segments). */
    private double bearing(int i) {
        int j = i + 1;
        while (j < lat.length - 1 && lat[j] == lat[i] && lon[j] == lon[i]) j++;
        double f1 = Math.toRadians(lat[i]), f2 = Math.toRadians(lat[j]), dl = Math.toRadians(lon[j] - lon[i]);
        double y = Math.sin(dl) * Math.cos(f2), x = Math.cos(f1) * Math.sin(f2) - Math.sin(f1) * Math.cos(f2) * Math.cos(dl);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }
}
