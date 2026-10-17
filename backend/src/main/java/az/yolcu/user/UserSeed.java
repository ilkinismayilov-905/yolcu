package az.yolcu.user;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Demo accounts. Called by seed.SeedRunner before the rest of the data. */
@Component
public class UserSeed {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public UserSeed(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    public void seedAccounts() {
        if (users.count() > 0) return;
        var hash = encoder.encode("demo123");
        users.save(new User("dispatcher@yolcu.az", hash, "Leyla Məmmədova", Role.DISPATCHER, 0));
        users.save(new User("admin@yolcu.az", hash, "Admin", Role.ADMIN, 0));
        users.save(new User(null, null, "Nigar Əliyeva", Role.PASSENGER, 245));
    }
}
