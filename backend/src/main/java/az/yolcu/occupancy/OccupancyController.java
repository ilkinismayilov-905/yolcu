package az.yolcu.occupancy;

import az.yolcu.occupancy.dto.AutoRequest;
import az.yolcu.occupancy.dto.Bunching;
import az.yolcu.occupancy.dto.IntervalRequest;
import az.yolcu.occupancy.dto.OccupancyBus;
import az.yolcu.occupancy.dto.RouteOccupancy;
import az.yolcu.occupancy.dto.SimulateResult;
import az.yolcu.common.ApiException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 03_API §9 "Doluluq və interval" (DISPATCHER, ADMIN via SecurityConfig /admin/**). */
@RestController
@RequestMapping("/api/v1/admin/occupancy")
public class OccupancyController {
    private final OccupancyService service;

    public OccupancyController(OccupancyService service) {
        this.service = service;
    }

    @GetMapping("/routes")
    public List<RouteOccupancy> routes() { return service.routes(); }

    @GetMapping("/routes/{route}/buses")
    public List<OccupancyBus> buses(@PathVariable String route) { return service.buses(route); }

    @PostMapping("/routes/{route}/interval")
    public RouteOccupancy interval(@PathVariable String route, @Valid @RequestBody IntervalRequest req) {
        return service.applyInterval(route, req.intervalMin());
    }

    @GetMapping("/simulate")
    public SimulateResult simulate(@RequestParam String route, @RequestParam int intervalMin) {
        if (intervalMin < IntervalEngine.MIN_INTERVAL || intervalMin > IntervalEngine.MAX_INTERVAL) {
            throw ApiException.validation("intervalMin: 6–16 dəqiqə arasında olmalıdır");
        }
        return service.simulate(route, intervalMin);
    }

    @PutMapping("/auto")
    public Map<String, Boolean> auto(@Valid @RequestBody AutoRequest req) {
        service.setAuto(req.enabled());
        return Map.of("enabled", req.enabled());
    }

    @GetMapping("/bunching")
    public List<Bunching> bunching() { return service.bunching(); }

    @PostMapping("/bunching/{route}/message")
    public Map<String, Object> message(@PathVariable String route) { return service.message(route); }
}
