package az.yolcu.fleet;

import az.yolcu.common.PageResponse;
import az.yolcu.fleet.dto.BusDetail;
import az.yolcu.fleet.dto.BusListItem;
import az.yolcu.fleet.dto.OperatorStats;
import az.yolcu.fleet.dto.SearchResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** DISPATCHER/ADMIN (SecurityConfig: /api/v1/admin/**). */
@RestController
@RequestMapping("/api/v1/admin")
public class FleetController {
    private final FleetService fleet;

    public FleetController(FleetService fleet) {
        this.fleet = fleet;
    }

    @GetMapping("/operators")
    public List<OperatorStats> operators() { return fleet.operators(); }

    @GetMapping("/buses")
    public PageResponse<BusListItem> buses(@RequestParam(required = false) String operator,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String type,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "12") int size) {
        return fleet.buses(operator, status, type, q, page, size);
    }

    @GetMapping("/buses/{bort}")
    public BusDetail bus(@PathVariable String bort) { return fleet.bus(bort); }

    @GetMapping("/search")
    public SearchResult search(@RequestParam(required = false) String q) { return fleet.search(q); }
}
