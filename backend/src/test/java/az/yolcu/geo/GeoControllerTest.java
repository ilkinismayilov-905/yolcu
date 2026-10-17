package az.yolcu.geo;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "yolcu.data-dir=src/test/resources/geo")
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class GeoControllerTest {
    @Autowired MockMvc mvc;

    @Test
    void layerFromFixture() throws Exception {
        mvc.perform(get("/api/v1/geo/layers/bike-lanes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features.length()").value(1));
    }

    @Test
    void unknownLayerIs404() throws Exception {
        mvc.perform(get("/api/v1/geo/layers/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void stopsNearElmler() throws Exception {
        mvc.perform(get("/api/v1/stops?lat=40.3770&lon=49.8180&radius=600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItems("elmler")))
                .andExpect(jsonPath("$[0].distanceMeters").value(222));
        // roadmap expects Nizami within 600 m, but mocks/stops_seed.json puts it ~1 040 m away
        mvc.perform(get("/api/v1/stops?lat=40.3770&lon=49.8180&radius=1100"))
                .andExpect(jsonPath("$[*].id", hasItems("elmler", "nizami")));
        mvc.perform(get("/api/v1/stops?lat=40.3770")).andExpect(status().isBadRequest());
    }

    @Test
    void places() throws Exception {
        mvc.perform(get("/api/v1/places"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("nerimanov"));
    }
}
