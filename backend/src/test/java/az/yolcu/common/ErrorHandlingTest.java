package az.yolcu.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** BE-10.2: bad input never gives 500 or Spring's default error body, always { error: { code, message } }. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ErrorHandlingTest {
    @Autowired MockMvc mvc;
    String dispatcher;
    String passenger;

    @BeforeEach
    void tokens() throws Exception {
        var d = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"dispatcher@yolcu.az\",\"password\":\"demo123\"}")).andReturn().getResponse().getContentAsString();
        dispatcher = "Bearer " + JsonPath.read(d, "$.token");
        var p = mvc.perform(post("/api/v1/auth/demo-login")).andReturn().getResponse().getContentAsString();
        passenger = "Bearer " + JsonPath.read(p, "$.token");
    }

    // who: D dispatcher, P passenger, - none. body '' = no body. type '' = application/json.
    @ParameterizedTest(name = "{0} {1} as {2} -> {5}")
    @CsvSource(delimiter = '|', nullValues = "~", value = {
            "GET   | /api/v1/admin/reports?page=2147483647&size=100 | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/reports?page=-1                  | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/reports?size=101                 | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/reports?size=abc                 | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/reports?status=BAD               | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/reports/A-9999                   | D | ~ | ~ | 404 | NOT_FOUND",
            "GET   | /api/v1/me/notifications?page=2147483647       | P | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/surveys?page=2147483647          | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/buses?page=2147483647            | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/coins/conversions?page=2147483647 | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/feed?limit=0                     | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/feed?limit=201                   | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/stops?lat=40.4&lon=49.8&radius=-5      | - | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/stops?lat=40.4&lon=49.8&radius=99999   | - | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/stops                                  | - | ~ | ~ | 400 | VALIDATION",
            "POST  | /api/v1/admin/reports/A-2041/share             | D | '{\"targets\":[\"FOO\"]}' | ~ | 400 | VALIDATION",
            "POST  | /api/v1/admin/reports/A-2041/share             | D | '{\"targets\":[null]}'    | ~ | 400 | VALIDATION",
            "POST  | /api/v1/auth/login                             | - | hello      | text/plain | 415 | VALIDATION",
            "POST  | /api/v1/auth/login                             | - | '{'        | ~ | 400 | VALIDATION",
            "POST  | /api/v1/auth/login                             | - | ~          | ~ | 400 | VALIDATION",
            "GET   | /api/v1/auth/login                             | - | ~ | ~ | 405 | VALIDATION",
            "DELETE| /api/v1/me                                     | P | ~ | ~ | 405 | VALIDATION",
            "PATCH | /api/v1/admin/reports/A-9999/status            | D | '{\"status\":\"REVIEW\"}' | ~ | 404 | NOT_FOUND",
            "GET   | /api/v1/admin/surveys/abc/results              | D | ~ | ~ | 400 | VALIDATION",
            "GET   | /api/v1/admin/reports                          | P | ~ | ~ | 403 | FORBIDDEN",
            "GET   | /api/v1/admin/reports                          | - | ~ | ~ | 401 | UNAUTHORIZED",
    })
    void standardError(String method, String path, String who, String body, String type, int code, String error)
            throws Exception {
        var req = request(HttpMethod.valueOf(method.trim()), path.trim());
        if (who.equals("D")) req.header("Authorization", dispatcher);
        if (who.equals("P")) req.header("Authorization", passenger);
        req.contentType(type == null ? MediaType.APPLICATION_JSON_VALUE : type);
        if (body != null) req.content(body);
        mvc.perform(req)
                .andExpect(status().is(code))
                .andExpect(jsonPath("$.error.code").value(error))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }
}
