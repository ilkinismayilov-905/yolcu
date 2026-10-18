package az.yolcu.auth;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class AuthFlowTest {
    @Autowired MockMvc mvc;

    @Test
    void meWithoutTokenIs401() throws Exception {
        mvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void demoLoginThenMe() throws Exception {
        var body = mvc.perform(post("/api/v1/auth/demo-login"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");

        mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.fullName").value("Nigar Əliyeva"))
                .andExpect(jsonPath("$.coinBalance").value(245));
        mvc.perform(get("/api/v1/me").param("token", token))
                .andExpect(jsonPath("$.role").value("PASSENGER"));
        mvc.perform(get("/api/v1/admin/anything").param("token", token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void dispatcherLogin() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dispatcher@yolcu.az\",\"password\":\"demoyolcu123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(startsWith("ey")))
                .andExpect(jsonPath("$.user.role").value("DISPATCHER"));
    }

    @Test
    void wrongPasswordAndBadBody() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dispatcher@yolcu.az\",\"password\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
    }

    private String dispatcherToken(String password) throws Exception {
        var body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dispatcher@yolcu.az\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String token, String current, String next) throws Exception {
        var req = post("/api/v1/me/password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"" + current + "\",\"newPassword\":\"" + next + "\"}");
        return mvc.perform(token == null ? req : req.header("Authorization", "Bearer " + token));
    }

    @Test
    @Transactional // rolls back so the seed password stays demoyolcu123 for other tests
    void changePasswordFlow() throws Exception {
        var token = dispatcherToken("demoyolcu123");
        changePassword(token, "wrong", "demoyolcu1234")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"))
                .andExpect(jsonPath("$.error.message").value("Hazırkı şifrə yanlışdır"));
        changePassword(token, "demoyolcu123", "abc")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        changePassword(token, "demoyolcu123", "demoyolcu123")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.message").value("Yeni şifrə hazırkı ilə eyni ola bilməz"));
        changePassword(token, "demoyolcu123", "demoyolcu1234")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
        dispatcherToken("demoyolcu1234");
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"dispatcher@yolcu.az\",\"password\":\"demoyolcu123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordWithoutTokenIs401() throws Exception {
        changePassword(null, "demoyolcu123", "demoyolcu1234")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }
}
