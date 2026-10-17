package az.yolcu.feed;

import az.yolcu.feed.dto.AlertDto;
import az.yolcu.feed.dto.Kpis;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class FeedController {
    private final FeedService feed;

    public FeedController(FeedService feed) {
        this.feed = feed;
    }

    @GetMapping("/feed")
    public List<AlertDto> feed(@RequestParam(defaultValue = "50") int limit) { return feed.feed(limit); }

    @GetMapping("/kpis")
    public Kpis kpis() { return feed.kpis(); }
}
