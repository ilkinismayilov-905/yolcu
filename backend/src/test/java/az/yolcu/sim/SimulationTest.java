package az.yolcu.sim;

import static org.assertj.core.api.Assertions.assertThat;

import az.yolcu.geo.GeoStore;
import az.yolcu.geo.Polyline;
import az.yolcu.sim.dto.BusPosition;
import az.yolcu.sim.dto.LiveHub;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** Scheduling is off in tests (application-h2.yml); ticks are called directly. */
@SpringBootTest
@ActiveProfiles("h2")
class SimulationTest {
    @Autowired SimulationService sim;
    @Autowired HubSimulator hubs;
    @Autowired GeoStore geo;
    @Autowired JdbcTemplate jdbc;

    Polyline line88() {
        return geo.routeLines().get("88").stream().max(Comparator.comparingDouble(Polyline::length)).orElseThrow();
    }

    @Test
    void busesMoveAlongTheRealTrajectory() {
        var first = sim.step().stream().filter(b -> b.route().equals("88")).toList();
        var second = sim.step().stream().filter(b -> b.route().equals("88")).toList();
        assertThat(first).hasSize(6);
        assertThat(first.get(0).bort()).isEqualTo("10-BB-482");
        var line = line88();
        for (int i = 0; i < first.size(); i++) {
            var a = first.get(i);
            var b = second.get(i);
            assertThat(b.bort()).isEqualTo(a.bort());
            assertThat(List.of(b.lat(), b.lon())).isNotEqualTo(List.of(a.lat(), a.lon()));
            assertThat(line.nearest(b.lat(), b.lon())[0]).isLessThan(30);
            assertThat(b.occupancy()).isBetween(8, 100);
            assertThat(b.bearing()).isBetween(0, 359);
        }
        assertThat(sim.buses("88")).isEqualTo(second);
    }

    @Test
    void etaToElmlerIsFinite() {
        sim.step();
        var eta = sim.etaMin("88", 40.3755, 49.8152);
        // 34.5 km loop, 6 buses at 22 km/h → a bus every ~16 min
        assertThat(eta).isNotNull().isBetween(0, 20);
        assertThat(sim.etaMin("nope", 40.3755, 49.8152)).isNull();
    }

    @Test
    void shorterIntervalLowersOccupancy() {
        try {
            for (int i = 0; i < 30; i++) sim.step();
            double before = avg88();
            jdbc.update("update route_config set interval_min = 6 where route = '88'");
            for (int i = 0; i < 30; i++) sim.step();
            assertThat(avg88()).isLessThan(before - 20); // base 88 % × 6/12 = 44 %
        } finally {
            jdbc.update("update route_config set interval_min = 12 where route = '88'");
            sim.step();
        }
    }

    double avg88() { return sim.buses("88").stream().mapToInt(BusPosition::occupancy).average().orElseThrow(); }

    @Test
    void hubCountsChangeAndUseMetroCoordinates() {
        var before = hubs.hubs();
        for (int i = 0; i < 3; i++) hubs.tick();
        var after = hubs.hubs();
        assertThat(after).hasSize(8);
        assertThat(after).extracting(LiveHub::count).isNotEqualTo(before.stream().map(LiveHub::count).toList());
        var koroglu = after.get(0);
        assertThat(koroglu.id()).isEqualTo("koroglu");
        assertThat(koroglu.lat()).isEqualTo(40.420514); // data/processed/metro_ridership.geojson "Koroğlu"
        assertThat(koroglu.forecast15()).isEqualTo((int) Math.round(koroglu.count() + koroglu.trend10() * 1.5));
        assertThat(hubs.totalWaiting()).isEqualTo(after.stream().mapToInt(LiveHub::count).sum());
    }
}
