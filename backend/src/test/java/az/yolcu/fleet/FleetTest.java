package az.yolcu.fleet;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
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
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class FleetTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;

    ResultActions call(String url) throws Exception {
        long id = jdbc.queryForObject("select id from users where email = 'dispatcher@yolcu.az'", Long.class);
        return mvc.perform(get(url).header("Authorization", "Bearer " + jwt.issue(id, "DISPATCHER")));
    }

    @Test
    void operatorCards() throws Exception {
        call("/api/v1/admin/operators").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].code").value("bb"))
                .andExpect(jsonPath("$[0].name").value("BakuBus MMC"))
                .andExpect(jsonPath("$[0].fleetTotal").value(640))
                .andExpect(jsonPath("$[0].punctuality").value(91))
                .andExpect(jsonPath("$[0].routes").value(hasItem("88")))
                .andExpect(jsonPath("$[*].onLine").value(everyItem(allOf(greaterThan(0), lessThanOrEqualTo(640)))))
                .andExpect(jsonPath("$[*].avgRating").value(everyItem(allOf(greaterThan(3.0), lessThanOrEqualTo(5.0)))))
                .andExpect(jsonPath("$[*].complaintsPer10k").value(everyItem(greaterThan(0.0))))
                .andExpect(jsonPath("$[1].code").value("xf"))
                .andExpect(jsonPath("$[1].avgRating").value(allOf(greaterThan(3.5), lessThanOrEqualTo(4.3))));
    }

    @Test
    void busesPagedAndFiltered() throws Exception {
        call("/api/v1/admin/buses?page=0&size=12").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(66))
                .andExpect(jsonPath("$.size").value(12))
                .andExpect(jsonPath("$.items.length()").value(12));
        call("/api/v1/admin/buses?page=5&size=12").andExpect(jsonPath("$.items.length()").value(6)); // 66 = 5 × 12 + 6
        call("/api/v1/admin/buses?page=6&size=12").andExpect(jsonPath("$.items.length()").value(0));
        call("/api/v1/admin/buses?operator=xf&size=100")
                .andExpect(jsonPath("$.total").value(greaterThan(0)))
                .andExpect(jsonPath("$.items[*].bort").value(everyItem(startsWith("99-XF-"))))
                .andExpect(jsonPath("$.items[*].operator").value(everyItem(org.hamcrest.Matchers.is("Xaliq Faiqoğlu MMC"))));
        call("/api/v1/admin/buses?status=DEPOT&size=100")
                .andExpect(jsonPath("$.total").value(greaterThan(0)))
                .andExpect(jsonPath("$.items[*].status").value(everyItem(org.hamcrest.Matchers.is("DEPOT"))))
                .andExpect(jsonPath("$.items[*].occupancy").value(everyItem(org.hamcrest.Matchers.nullValue())));
        call("/api/v1/admin/buses?type=ELECTRIC&size=100").andExpect(jsonPath("$.total").value(21));
        call("/api/v1/admin/buses?q=10-bb-48").andExpect(jsonPath("$.items[*].bort").value(hasItem("10-BB-482")));
        call("/api/v1/admin/buses?status=REPAIR").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        for (var bad : new String[]{"status=BROKEN", "type=GAS", "operator=zz", "size=0", "page=-1"}) {
            call("/api/v1/admin/buses?" + bad).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION"));
        }
    }

    @Test
    void busDetail() throws Exception {
        call("/api/v1/admin/buses/10-BB-120").andExpect(status().isOk())
                .andExpect(jsonPath("$.bort").value("10-BB-120"))
                .andExpect(jsonPath("$.operatorCode").value("bb"))
                .andExpect(jsonPath("$.reports.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.reports[*].bort").value(everyItem(org.hamcrest.Matchers.is("10-BB-120"))))
                .andExpect(jsonPath("$.reviews.length()").value(lessThanOrEqualTo(5)));
        String withReviews = jdbc.queryForObject("select bort from reviews group by bort order by count(*) desc, bort limit 1", String.class);
        call("/api/v1/admin/buses/" + withReviews).andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(greaterThan(0)))
                .andExpect(jsonPath("$.reviews[0].driverRating").exists())
                .andExpect(jsonPath("$.reviews[0].tags").isArray())
                .andExpect(jsonPath("$.driverRating").isNumber());
        call("/api/v1/admin/buses/00-ZZ-000").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void search() throws Exception {
        call("/api/v1/admin/search?q=10-BB-482").andExpect(jsonPath("$.type").value("BUS")).andExpect(jsonPath("$.ref").value("10-BB-482"));
        call("/api/v1/admin/search?q=10bb482").andExpect(jsonPath("$.ref").value("10-BB-482"));
        call("/api/v1/admin/search?q=a2041").andExpect(jsonPath("$.type").value("REPORT")).andExpect(jsonPath("$.ref").value("A-2041"));
        call("/api/v1/admin/search?q=A-99999").andExpect(jsonPath("$.type").value("NONE"));
        call("/api/v1/admin/search?q=xyz").andExpect(jsonPath("$.type").value("NONE")).andExpect(jsonPath("$.ref").doesNotExist());
    }

    @Test
    void passengerForbidden() throws Exception {
        long p = jdbc.queryForObject("select min(id) from users where role = 'PASSENGER'", Long.class);
        for (var url : new String[]{"/api/v1/admin/operators", "/api/v1/admin/buses", "/api/v1/admin/search?q=x"}) {
            mvc.perform(get(url).header("Authorization", "Bearer " + jwt.issue(p, "PASSENGER"))).andExpect(status().isForbidden());
        }
    }
}
