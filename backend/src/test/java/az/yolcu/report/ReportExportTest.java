package az.yolcu.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import az.yolcu.security.JwtService;
import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
@Transactional
class ReportExportTest {
    static final String HEADER = "ID;Vaxt;Marşrut;Bort;Operator;Mövzu;Mətn;Status;Yer;Gecikir";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;

    String dispatcher() {
        return "Bearer " + jwt.issue(jdbc.queryForObject("select id from users where email = 'dispatcher@yolcu.az'", Long.class), "DISPATCHER");
    }

    String passenger() {
        return "Bearer " + jwt.issue(jdbc.queryForObject("select id from users where role = 'PASSENGER' order by id limit 1 offset 3", Long.class), "PASSENGER");
    }

    byte[] export(String query) throws Exception {
        return mvc.perform(get("/api/v1/admin/reports/export.csv" + query).header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    static String[] lines(byte[] body) {
        return new String(body, 3, body.length - 3, StandardCharsets.UTF_8).split("\r\n");
    }

    @Test
    void exportAllWithHeadersAndBom() throws Exception {
        long all = jdbc.queryForObject("select count(*) from reports", Long.class);
        var res = mvc.perform(get("/api/v1/admin/reports/export.csv").header("Authorization", dispatcher()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.matchesPattern("attachment; filename=\"sikayetler-\\d{4}-\\d{2}-\\d{2}\\.csv\"")))
                .andExpect(header().string("X-Total-Count", String.valueOf(all)))
                .andReturn().getResponse();
        byte[] body = res.getContentAsByteArray();
        assertThat(Arrays.copyOf(body, 3)).containsExactly(0xEF, 0xBB, 0xBF);
        var lines = lines(body);
        assertThat(lines[0]).isEqualTo(HEADER);
        assertThat(lines).hasSize((int) all + 1);
        assertThat(lines[1]).startsWith("A-2041;").matches(".*;(Bəli|Xeyr)$");
    }

    @Test
    void statusFilterMatchesTotal() throws Exception {
        long fresh = jdbc.queryForObject("select count(*) from reports where status = 'NEW'", Long.class);
        var res = mvc.perform(get("/api/v1/admin/reports/export.csv?status=NEW").header("Authorization", dispatcher()))
                .andExpect(header().string("X-Total-Count", String.valueOf(fresh)))
                .andReturn().getResponse();
        var lines = lines(res.getContentAsByteArray());
        assertThat(lines).hasSize((int) fresh + 1);
        for (int i = 1; i < lines.length; i++) assertThat(lines[i].split(";")[7]).isEqualTo("Yeni");
        mvc.perform(get("/api/v1/admin/reports/export.csv?status=BAD").header("Authorization", dispatcher()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void escapesPassengerText() throws Exception {
        var body = "{\"route\":\"88\",\"category\":\"Doluluq\",\"text\":\"=Dolu; \\\"çox\\\"\\nsətir 2\"}";
        var created = mvc.perform(post("/api/v1/me/reports").header("Authorization", passenger())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(created, "$.code");
        var csv = new String(export("?q=" + code), StandardCharsets.UTF_8);
        assertThat(csv).contains(";\"'=Dolu; \"\"çox\"\"\nsətir 2\";Yeni;");
    }

    @Test
    void passengerForbidden() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/export.csv").header("Authorization", passenger()))
                .andExpect(status().isForbidden());
    }
}
