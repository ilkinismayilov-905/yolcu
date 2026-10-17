package az.yolcu.traffic;

import static org.assertj.core.api.Assertions.assertThat;

import az.yolcu.geo.GeoStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("h2")
class TrafficServiceTest {
    @Autowired TrafficService traffic;
    @Autowired GeoStore geo;

    @Test
    void segmentsFollowTheRealTrajectory() {
        var matches = traffic.matches();
        assertThat(matches).containsKeys("yusif-seferov", "babek");
        matches.forEach((id, m) -> {
            assertThat(m.line()).as(id).hasSizeGreaterThan(2);
            assertThat(m.maxAnchorM()).as(id).isLessThanOrEqualTo(TrafficService.MAX_ANCHOR_M);
            var source = geo.routeLines().get(m.route());
            for (var p : m.line()) {
                double d = source.stream().mapToDouble(l -> l.nearest(p[0], p[1])[0]).min().orElseThrow();
                assertThat(d).as(id + " point on route " + m.route()).isLessThan(1);
            }
        });
        assertThat(traffic.segments()).extracting(s -> s.id()).containsExactlyElementsOf(matches.keySet());
        assertThat(traffic.segments()).allSatisfy(s -> assertThat(s.level()).isIn("FREE", "SLOW", "JAM"));
    }
}
