package az.yolcu.analytics;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AnalyticsTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;

    String auth() {
        return "Bearer " + jwt.issue(jdbc.queryForObject("select id from users where email = 'dispatcher@yolcu.az'", Long.class), "DISPATCHER");
    }

    @Test
    void kpisFromTrips() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/kpis").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackedTrips30d").value(greaterThan(4000)))
                .andExpect(jsonPath("$.consentUsers").value(greaterThan(10)))
                .andExpect(jsonPath("$.avgTripMin").value(org.hamcrest.Matchers.allOf(greaterThan(12), org.hamcrest.Matchers.lessThan(46))))
                .andExpect(jsonPath("$.longestTransferWaitMin").value(12))
                .andExpect(jsonPath("$.suggestionAcceptance").value(41));
    }

    @Test
    void odMatrixSixZones() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/od?from=07:00&to=10:00").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.zones.length()").value(6))
                .andExpect(jsonPath("$.zones[0]").value("Avtovağzal"))
                .andExpect(jsonPath("$.matrix.length()").value(6))
                .andExpect(jsonPath("$.matrix[0].length()").value(6))
                .andExpect(jsonPath("$.matrix[0][0]").value(0))
                .andExpect(jsonPath("$.matrix[0][2]").value(greaterThan(100))); // Avtovağzal → 28 May is the heaviest OD weight
        // 70 % of synthetic trips are in 07–10, so the full day has more
        mvc.perform(get("/api/v1/admin/analytics/od?from=00:00&to=23:59").header("Authorization", auth()))
                .andExpect(jsonPath("$.matrix[0][2]").value(greaterThan(300)));
        for (var bad : new String[]{"from=7:00", "from=10:00&to=09:00", "to=24:00"}) {
            mvc.perform(get("/api/v1/admin/analytics/od?" + bad).header("Authorization", auth()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        }
    }

    @Test
    void alightingTop() throws Exception {
        mvc.perform(get("/api/v1/admin/analytics/alighting?limit=5").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].stop").value("28 May"))
                .andExpect(jsonPath("$[0].perDay").value(greaterThan(10)))
                .andExpect(jsonPath("$[0].peak").value(org.hamcrest.Matchers.matchesPattern("\\d\\d:\\d[05]")))
                .andExpect(jsonPath("$[0].note").value(org.hamcrest.Matchers.containsString("→ 28 May axını")));
        mvc.perform(get("/api/v1/admin/analytics/alighting?limit=0").header("Authorization", auth()))
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        long p = jdbc.queryForObject("select min(id) from users where role = 'PASSENGER'", Long.class);
        mvc.perform(get("/api/v1/admin/analytics/kpis").header("Authorization", "Bearer " + jwt.issue(p, "PASSENGER")))
                .andExpect(status().isForbidden());
    }
}
