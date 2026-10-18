package az.yolcu.hub;

import az.yolcu.hub.dto.AdminHub;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 03_API §9 "Hub kameraları" (DISPATCHER, ADMIN via SecurityConfig /admin/**). */
@RestController
@RequestMapping("/api/v1/admin/hubs")
public class HubController {
    private final HubService service;

    public HubController(HubService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminHub> hubs() { return service.hubs(); }

    @PostMapping("/{id}/apply")
    public Map<String, Boolean> apply(@PathVariable String id) {
        service.apply(id);
        return Map.of("ok", true);
    }
}
