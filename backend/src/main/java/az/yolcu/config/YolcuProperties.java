package az.yolcu.config;

import java.time.LocalTime;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("yolcu")
public record YolcuProperties(
        String jwtSecret,
        List<String> corsOrigins,
        String dataDir,
        String mocksDir,
        int coinRate,
        Demo demo) {

    public record Demo(LocalTime clockStart, boolean autoAdvanceReports, int bankDelaySeconds) {}
}
