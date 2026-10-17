package az.yolcu.geo;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Real data/processed layers. */
@SpringBootTest
@ActiveProfiles("h2")
class GeoStoreTest {
    @Autowired GeoStore geo;

    @Test
    void routesLiteIsSmallerWithSameFeatures() {
        var full = geo.get("routes");
        var lite = geo.get("routes-lite");
        assertThat(lite.path("features").size()).isEqualTo(full.path("features").size()).isEqualTo(243);
        assertThat(geo.bytes("routes-lite").body().length).isLessThan(geo.bytes("routes").body().length / 3);
        var f = lite.path("features").get(0);
        assertThat(f.path("properties").has("route")).isTrue();
        assertThat(f.path("properties").size()).isEqualTo(2);
        assertThat(geo.routeLines().keySet()).contains("88", "4", "65", "33", "M1", "H1");
    }

    @Test
    void douglasPeuckerKeepsCornersDropsCollinear() {
        double[] x = {0, 1, 2, 2, 2}, y = {0, 0.00001, 0, 1, 2};
        assertThat(GeoStore.douglasPeucker(x, y, 0.0001)).containsExactly(true, false, true, false, true);
    }
}
