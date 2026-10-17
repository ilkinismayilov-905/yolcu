package az.yolcu.live;

import az.yolcu.micro.MicromobilityService;
import az.yolcu.micro.dto.Micromobility;
import az.yolcu.sim.HubSimulator;
import az.yolcu.sim.SimulationService;
import az.yolcu.sim.dto.BusPosition;
import az.yolcu.sim.dto.LiveHub;
import az.yolcu.traffic.TrafficService;
import az.yolcu.traffic.dto.TrafficSegment;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 03_API_CONTRACT §8 (+ §5 micromobility). Any signed-in user. */
@RestController
@RequestMapping("/api/v1/live")
public class LiveController {
    private final SimulationService sim;
    private final HubSimulator hubs;
    private final TrafficService traffic;
    private final MicromobilityService micro;

    public LiveController(SimulationService sim, HubSimulator hubs, TrafficService traffic, MicromobilityService micro) {
        this.sim = sim;
        this.hubs = hubs;
        this.traffic = traffic;
        this.micro = micro;
    }

    @GetMapping("/buses")
    public List<BusPosition> buses(@RequestParam(required = false) String route) { return sim.buses(route); }

    @GetMapping("/traffic")
    public List<TrafficSegment> traffic() { return traffic.segments(); }

    @GetMapping("/hubs")
    public List<LiveHub> hubs() { return hubs.hubs(); }

    @GetMapping("/micromobility")
    public Micromobility micromobility(@RequestParam double lat, @RequestParam double lon,
                                       @RequestParam(defaultValue = "800") double radius) {
        return micro.near(lat, lon, radius);
    }
}
