package az.yolcu.live;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.security.JwtService;
import az.yolcu.sim.SimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class LiveControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JwtService jwt;
    @Autowired SimulationService sim;

    String auth() { return "Bearer " + jwt.issue(1L, "PASSENGER"); }

    @Test
    void liveEndpoints() throws Exception {
        sim.step();
        mvc.perform(get("/api/v1/live/buses?route=88").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[0].bort").value("10-BB-482"))
                .andExpect(jsonPath("$[0].capacity").value(90));
        mvc.perform(get("/api/v1/live/hubs").header("Authorization", auth()))
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[0].trend10").isNumber());
        mvc.perform(get("/api/v1/live/traffic").header("Authorization", auth()))
                .andExpect(jsonPath("$[0].line.length()").value(greaterThan(2)));
        mvc.perform(get("/api/v1/live/buses")).andExpect(status().isUnauthorized());
    }

    @Test
    void micromobilityNearElmler() throws Exception {
        mvc.perform(get("/api/v1/live/micromobility?lat=40.3755&lon=49.8152").header("Authorization", auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bikeParkings.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.scooters.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.bikeLanesLayer").value("/api/v1/geo/layers/bike-lanes"));
        mvc.perform(get("/api/v1/live/micromobility?lat=40.3755").header("Authorization", auth()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }
}
