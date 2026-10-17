package az.yolcu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class YolcuApplication {
    public static void main(String[] args) {
        SpringApplication.run(YolcuApplication.class, args);
    }
}
