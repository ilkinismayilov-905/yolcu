package az.yolcu.analytics;

import az.yolcu.analytics.dto.AlightingPoint;
import az.yolcu.analytics.dto.AnalyticsKpis;
import az.yolcu.analytics.dto.OdMatrix;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AnalyticsController {
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/kpis")
    public AnalyticsKpis kpis() { return analytics.kpis(); }

    @GetMapping("/od")
    public OdMatrix od(@RequestParam(defaultValue = "07:00") String from, @RequestParam(defaultValue = "10:00") String to) {
        return analytics.od(from, to);
    }

    @GetMapping("/alighting")
    public List<AlightingPoint> alighting(@RequestParam(defaultValue = "5") int limit) { return analytics.alighting(limit); }
}
