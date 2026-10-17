package az.yolcu.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("h2")
class SeedRunnerTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SeedRunner seed;

    long count(String sql) { return jdbc.queryForObject(sql, Long.class); }

    @Test
    void seedsMocksAndIsIdempotent() throws Exception {
        assertThat(count("select count(*) from buses")).isEqualTo(66);
        assertThat(count("select count(*) from operators")).isEqualTo(4);
        assertThat(count("select count(*) from reports")).isEqualTo(40);
        assertThat(count("select count(*) from trips where synthetic")).isEqualTo(SyntheticGenerator.TRIPS);
        assertThat(count("select count(*) from reviews")).isEqualTo(SyntheticGenerator.REVIEWS + 1);
        long demo = count("select min(id) from users where role = 'PASSENGER'");
        assertThat(count("select count(*) from trips where user_id = " + demo + " and route = '88'")).isEqualTo(12);
        assertThat(count("select count(*) from trips where user_id = " + demo + " and route = '65'")).isEqualTo(5);
        assertThat(count("select sum(amount) from coin_tx where user_id = " + demo)).isEqualTo(245);
        assertThat(count("select count(*) from reports where status = 'FORWARDED' and forwarded_at < now() - interval '24' hour"))
                .isEqualTo(4);

        seed.run();
        assertThat(count("select count(*) from buses")).isEqualTo(66);
    }

    @Test
    void operatorRatingsNearTarget() {
        double bb = jdbc.queryForObject("select avg(driver_rating) from reviews r join operators o on o.id = r.operator_id "
                + "where o.code = '6d'", Double.class);
        assertThat(bb).isBetween(3.2, 3.8);
    }
}
