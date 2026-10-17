package az.yolcu.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the Next.js static export (classpath:/static, built with -Pweb).
 * Extensionless paths resolve to static/&lt;path&gt;/index.html, else /index.html, so reloading /reports/ works.
 */
@Configuration
public class SpaConfig implements WebMvcConfigurer {

    private static final String[] BACKEND_PREFIXES = {"api/", "swagger-ui", "v3/", "actuator"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String path, Resource location) throws IOException {
                        // super.getResource keeps Spring's readable + path-traversal checks
                        if (!path.isEmpty() && !path.endsWith("/")) {
                            var file = super.getResource(path, location);
                            if (file != null) return file;
                        }
                        if (isBackend(path) || lastSegment(path).contains(".")) return null;
                        var page = super.getResource(path.isEmpty() || path.endsWith("/")
                                ? path + "index.html" : path + "/index.html", location);
                        return page != null ? page : super.getResource("index.html", location);
                    }
                });
    }

    static boolean isBackend(String path) {
        for (var p : BACKEND_PREFIXES) if (path.startsWith(p)) return true;
        return false;
    }

    private static String lastSegment(String path) {
        var trimmed = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }
}
