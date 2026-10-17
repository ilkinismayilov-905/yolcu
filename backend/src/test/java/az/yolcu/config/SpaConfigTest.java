package az.yolcu.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Uses fixture pages in src/test/resources/static. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class SpaConfigTest {
    @Autowired MockMvc mvc;

    @Test
    void pagesResolveWithoutAuth() throws Exception {
        mvc.perform(get("/reports/")).andExpect(status().isOk()).andExpect(content().string(containsString("reports")));
        mvc.perform(get("/reports")).andExpect(status().isOk()).andExpect(content().string(containsString("reports")));
        // "/" goes through Spring Boot's welcome-page forward (MockMvc does not follow forwards)
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/no/such/page/")).andExpect(status().isOk()).andExpect(content().string(containsString("root")));
    }

    @Test
    void filesAndApiAreNotRewritten() throws Exception {
        mvc.perform(get("/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/nope")).andExpect(status().isUnauthorized());
    }
}
