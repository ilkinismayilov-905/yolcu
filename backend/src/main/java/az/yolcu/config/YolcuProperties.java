package az.yolcu.config;

import java.nio.file.Files;
import java.nio.file.Path;
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

    public Path dataPath() { return resolve(dataDir); }
    public Path mocksPath() { return resolve(mocksDir); }

    /** Paths are relative to backend/; also works when started from the repo root ("../x" → "x"). */
    static Path resolve(String dir) {
        var p = Path.of(dir);
        if (Files.isDirectory(p) || p.isAbsolute() || !dir.startsWith("../")) return p;
        var fromRoot = Path.of(dir.substring(3));
        return Files.isDirectory(fromRoot) ? fromRoot : p;
    }

    public record Demo(LocalTime clockStart, boolean autoAdvanceReports, int bankDelaySeconds) {}
}
