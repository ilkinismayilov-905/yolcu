package az.yolcu.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Tests set yolcu.scheduling=false and call the simulators' tick() directly, so they are deterministic. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "yolcu.scheduling", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {}
