package az.yolcu.biletim;

import az.yolcu.biletim.dto.BiletimArrival;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 03_API §9 "Avtovağzal və Biletim.az" (DISPATCHER, ADMIN via SecurityConfig /admin/**). */
@RestController
@RequestMapping("/api/v1/admin/biletim")
public class BiletimController {
    private final BiletimService service;

    public BiletimController(BiletimService service) {
        this.service = service;
    }

    @GetMapping("/arrivals")
    public List<BiletimArrival> arrivals() { return service.arrivals(); }
}
