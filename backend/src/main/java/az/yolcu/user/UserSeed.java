package az.yolcu.user;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Demo accounts. The full SeedRunner (BE-1.3) adds the rest of the data. */
@Component
public class UserSeed implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserSeed(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.count() > 0) return;
        var hash = encoder.encode("demo123");
        users.save(new User("dispatcher@yolcu.az", hash, "Leyla Məmmədova", Role.DISPATCHER, 0));
        users.save(new User("admin@yolcu.az", hash, "Admin", Role.ADMIN, 0));
        users.save(new User(null, null, "Nigar Əliyeva", Role.PASSENGER, 245));
    }
}
