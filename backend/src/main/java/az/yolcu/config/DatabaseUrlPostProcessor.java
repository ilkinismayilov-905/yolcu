package az.yolcu.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * prod only: turns Render's DATABASE_URL (postgresql://user:pass@host[:port]/db[?params]) into spring.datasource.*.
 * Placed right after the OS environment, so application.yml loses but SPRING_DATASOURCE_URL / -D / args still win.
 */
public class DatabaseUrlPostProcessor implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        var url = env.getProperty("DATABASE_URL");
        if (url == null || url.isBlank() || !env.matchesProfiles("prod")) return;
        var source = new MapPropertySource("databaseUrl", parse(url));
        var sys = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
        if (env.getPropertySources().contains(sys)) env.getPropertySources().addAfter(sys, source);
        else env.getPropertySources().addLast(source);
    }

    /** Runs after config files are loaded, so spring.profiles.active from any source is known. */
    @Override
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }

    static Map<String, Object> parse(String url) {
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw invalid("not a valid URI (URL-encode special characters in user/password)");
        }
        var scheme = uri.getScheme();
        if (!"postgresql".equals(scheme) && !"postgres".equals(scheme)) throw invalid("scheme must be postgresql:// or postgres://");
        if (uri.getHost() == null) throw invalid("host is missing");
        var path = uri.getRawPath();
        if (path == null || path.length() < 2) throw invalid("database name is missing");

        var jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + (uri.getPort() == -1 ? 5432 : uri.getPort()) + path
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        var props = new LinkedHashMap<String, Object>();
        props.put("spring.datasource.url", jdbc);
        var info = uri.getRawUserInfo();
        if (info != null) {
            int colon = info.indexOf(':');
            props.put("spring.datasource.username", decode(colon < 0 ? info : info.substring(0, colon)));
            if (colon >= 0) props.put("spring.datasource.password", decode(info.substring(colon + 1)));
        }
        return props;
    }

    // '+' is literal in userinfo, URLDecoder would turn it into a space
    private static String decode(String s) {
        return URLDecoder.decode(s.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    // never echo the URL: it contains the password
    private static IllegalStateException invalid(String why) {
        return new IllegalStateException("DATABASE_URL is invalid: " + why
                + ". Expected postgresql://user:pass@host[:port]/db[?params]");
    }
}
