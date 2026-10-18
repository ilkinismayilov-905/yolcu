package az.yolcu.occupancy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** 02_ARCHITECTURE §7 vectors, identical to packages/shared/src/headway.test.ts. */
class IntervalEngineTest {
    @ParameterizedTest(name = "route {0}")
    @CsvSource({
            "88, 92, 12, 6, 8, 61, 9, 0.7",
            "4, 81, 10, 5, 8, 65, 7, 0.4",
            "M1, 76, 10, 3, 8, 61, 4, 0.3",
            "65, 58, 12, 4, 12, 58, 4, 0",
            "33, 31, 12, 5, 16, 41, 4, -0.2"})
    void vectors(String route, int occ, int iv, int n, int rec, int occNew, int buses, double satDelta) {
        var r = IntervalEngine.simulate(occ, iv, n);
        assertThat(r.intervalMin()).isEqualTo(rec);
        assertThat(r.occupancy()).isEqualTo(occNew);
        assertThat(r.busesNeeded()).isEqualTo(buses);
        assertThat(r.satDelta()).isEqualTo(satDelta);
    }

    @Test
    void contractSamples() {
        var r88 = IntervalEngine.simulate(92, 12, 6);
        assertThat(r88.satNow()).isEqualTo(3.8);
        assertThat(r88.satAfter()).isEqualTo(4.5);
        // 03_API §9 simulate?route=88&intervalMin=10 → occupancy 77, waitMin 5, busesNeeded 8, sat 4.2
        var r10 = IntervalEngine.simulate(92, 12, 6, 10);
        assertThat(r10.occupancy()).isEqualTo(77);
        assertThat(r10.waitMin()).isEqualTo(5);
        assertThat(r10.busesNeeded()).isEqualTo(8);
        assertThat(r10.satAfter()).isEqualTo(4.2);
    }
}
