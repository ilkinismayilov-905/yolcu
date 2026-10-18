package az.yolcu.user;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Demo accounts. Called by seed.SeedRunner before the rest of the data. */
@Component
public class UserSeed {
    private static final Logger log = LoggerFactory.getLogger(UserSeed.class);
    private static final List<String> DEMO_EMAILS = List.of("dispatcher@yolcu.az", "admin@yolcu.az");

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String password;
    private final boolean syncPassword;

    public UserSeed(UserRepository users, PasswordEncoder encoder,
                    @Value("${yolcu.demo.password:}") String password,
                    @Value("${yolcu.demo.sync-password:false}") boolean syncPassword) {
        if (password == null || password.isBlank())
            throw new IllegalStateException("DEMO_PASSWORD (yolcu.demo.password) is required for the demo accounts");
        this.users = users;
        this.encoder = encoder;
        this.password = password;
        this.syncPassword = syncPassword;
    }

    public void seedAccounts() {
        if (users.count() > 0) {
            if (syncPassword) syncDemoPasswords();
            return;
        }
        var hash = encoder.encode(password);
        users.save(new User("dispatcher@yolcu.az", hash, "Leyla Məmmədova", Role.DISPATCHER, 0));
        users.save(new User("admin@yolcu.az", hash, "Admin", Role.ADMIN, 0));
        users.save(new User(null, null, "Nigar Əliyeva", Role.PASSENGER, 245));
    }

    /** prod: changing DEMO_PASSWORD on Render takes effect on the next start. */
    void syncDemoPasswords() {
        for (var email : DEMO_EMAILS) {
            users.findByEmailIgnoreCase(email)
                    .filter(u -> u.getPasswordHash() == null || !encoder.matches(password, u.getPasswordHash()))
                    .ifPresent(u -> {
                        u.setPasswordHash(encoder.encode(password));
                        users.save(u);
                        log.info("Demo account {}: password updated from DEMO_PASSWORD", email);
                    });
        }
    }
}
