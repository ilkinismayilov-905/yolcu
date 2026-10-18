package az.yolcu.coin;

import java.time.OffsetDateTime;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** SIM: the partner bank answers yolcu.demo.bank-delay-seconds after a conversion request (checked every second). */
@Component
public class BankSimJob {
    private final ConversionService conversions;

    public BankSimJob(ConversionService conversions) {
        this.conversions = conversions;
    }

    @Scheduled(fixedDelay = 1_000, initialDelay = 5_000)
    void tick() {
        conversions.completeDue(OffsetDateTime.now());
    }
}
