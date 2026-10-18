package az.yolcu.demo;

import az.yolcu.common.ApiException;
import az.yolcu.demo.dto.DemoResult;
import az.yolcu.demo.dto.ResetResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Roles (DISPATCHER, ADMIN) are enforced in SecurityConfig; yolcu.demo.enabled=false → 404 NOT_FOUND. */
@RestController
@RequestMapping("/api/v1/demo")
public class DemoController {
    private final DemoService demo;
    boolean enabled;

    public DemoController(DemoService demo, @Value("${yolcu.demo.enabled:true}") boolean enabled) {
        this.demo = demo;
        this.enabled = enabled;
    }

    @PostMapping("/trigger/{scenario}")
    public DemoResult trigger(@PathVariable String scenario) {
        gate();
        return demo.trigger(scenario);
    }

    @PostMapping("/reset")
    public ResetResult reset() {
        gate();
        return demo.reset();
    }

    private void gate() {
        if (!enabled) throw ApiException.notFound("Demo rejimi söndürülüb (yolcu.demo.enabled=false)");
    }
}
