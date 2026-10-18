package az.yolcu.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

class DatabaseUrlPostProcessorTest {

    @Test
    void renderUrlWithPortAndParams() {
        assertThat(DatabaseUrlPostProcessor.parse("postgresql://yolcu:s3cret@dpg-abc.oregon-postgres.render.com:6543/yolcu_db?sslmode=require"))
                .containsEntry("spring.datasource.url", "jdbc:postgresql://dpg-abc.oregon-postgres.render.com:6543/yolcu_db?sslmode=require")
                .containsEntry("spring.datasource.username", "yolcu")
                .containsEntry("spring.datasource.password", "s3cret");
    }

    @Test
    void missingPortDefaultsAndPostgresSchemeAccepted() {
        assertThat(DatabaseUrlPostProcessor.parse("postgres://u:p@dpg-abc-a/db"))
                .containsEntry("spring.datasource.url", "jdbc:postgresql://dpg-abc-a:5432/db");
    }

    @Test
    void encodedUserAndPasswordAreDecoded() {
        var p = DatabaseUrlPostProcessor.parse("postgresql://us%40er:p%3Aa%2Fs%40s+1@host/db");
        assertThat(p).containsEntry("spring.datasource.username", "us@er")
                .containsEntry("spring.datasource.password", "p:a/s@s+1");
    }

    @Test
    void malformedGivesClearErrorWithoutPassword() {
        for (var bad : new String[] {"mysql://u:secretpw@h/db", "postgresql://u:secretpw@h", "postgresql://u:secretpw@h/", "not a url secretpw"}) {
            assertThatThrownBy(() -> DatabaseUrlPostProcessor.parse(bad))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageStartingWith("DATABASE_URL is invalid")
                    .hasMessageNotContaining("secretpw");
        }
    }

    @Test
    void onlyInProdAndExplicitDatasourceUrlWins() {
        var env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("t", Map.of("DATABASE_URL", "postgres://u:p@h/db")));
        new DatabaseUrlPostProcessor().postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.datasource.url")).isNull();

        env.setActiveProfiles("prod");
        new DatabaseUrlPostProcessor().postProcessEnvironment(env, null);
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://h:5432/db");

        env.getPropertySources().addFirst(new MapPropertySource("cli", Map.of("spring.datasource.url", "jdbc:postgresql://x/y")));
        assertThat(env.getProperty("spring.datasource.url")).isEqualTo("jdbc:postgresql://x/y");
    }

    /** Spring Boot 3 loads EnvironmentPostProcessors from META-INF/spring.factories only (not META-INF/spring/*.imports). */
    @Test
    void registeredInSpringFactories() {
        var names = new java.util.ArrayList<String>();
        try {
            var urls = getClass().getClassLoader().getResources("META-INF/spring.factories");
            while (urls.hasMoreElements()) {
                var props = new java.util.Properties();
                try (var in = urls.nextElement().openStream()) { props.load(in); }
                var v = props.getProperty("org.springframework.boot.env.EnvironmentPostProcessor");
                if (v != null) for (var n : v.split(",")) names.add(n.trim());
            }
        } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        assertThat(names).contains(DatabaseUrlPostProcessor.class.getName());
    }
}
