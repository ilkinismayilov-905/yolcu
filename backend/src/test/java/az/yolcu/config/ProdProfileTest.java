package az.yolcu.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.YolcuApplication;
import az.yolcu.security.JwtService;
import az.yolcu.user.UserSeed;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** prod profile on its own H2 DB (the other tests share jdbc:h2:mem:yolcu). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prodtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "JWT_SECRET=prod-test-secret-0123456789-abcdefghij",
        "DEMO_PASSWORD=prod-pass",
        "DEMO_ENABLED=true"})
@AutoConfigureMockMvc
@ActiveProfiles({"h2", "prod"})
class ProdProfileTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @Autowired UserSeed seed;
    @Autowired PasswordEncoder encoder;

    String bearer(String email, String role) {
        return "Bearer " + jwt.issue(jdbc.queryForObject("select id from users where email = ?", Long.class, email), role);
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void swaggerIsOff() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().isNotFound());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }

    @Test
    void demoIsAdminOnly() throws Exception {
        mvc.perform(post("/api/v1/demo/trigger/nope").header("Authorization", bearer("dispatcher@yolcu.az", "DISPATCHER")))
                .andExpect(status().isForbidden());
        // past security: unknown scenario is the controller's 404
        mvc.perform(post("/api/v1/demo/trigger/nope").header("Authorization", bearer("admin@yolcu.az", "ADMIN")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void demoPasswordFromEnvAndResynced() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"email\":\"admin@yolcu.az\",\"password\":\"prod-pass\"}"))
                .andExpect(status().isOk());
        jdbc.update("update users set password_hash = ? where email = 'dispatcher@yolcu.az'", encoder.encode("old"));
        seed.seedAccounts();
        var hash = jdbc.queryForObject("select password_hash from users where email = 'dispatcher@yolcu.az'", String.class);
        assertThat(encoder.matches("prod-pass", hash)).isTrue();
    }

    @Test
    void missingJwtSecretFailsStartup() {
        var app = new SpringApplicationBuilder(YolcuApplication.class).profiles("h2", "prod").properties(
                "spring.datasource.url=jdbc:h2:mem:prodfail;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
                "server.port=0", "yolcu.scheduling=false", "DEMO_PASSWORD=x");
        assertThatThrownBy(() -> app.run("--JWT_SECRET=")).rootCause().hasMessageContaining("JWT_SECRET");
    }
}
