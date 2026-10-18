package az.yolcu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// JWT only: no in-memory default user (and no "Using generated security password" WARN)
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class YolcuApplication {
    public static void main(String[] args) {
        SpringApplication.run(YolcuApplication.class, args);
    }
}
