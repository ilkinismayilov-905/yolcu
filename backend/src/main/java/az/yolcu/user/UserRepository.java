package az.yolcu.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findFirstByRoleOrderByIdAsc(Role role);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.coinBalance = u.coinBalance + :delta where u.id = :id")
    int addCoins(Long id, int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.coinBalance = u.coinBalance - :amount where u.id = :id and u.coinBalance >= :amount")
    int spendCoins(Long id, int amount);

    @Query("select u.coinBalance from User u where u.id = :id")
    int coinBalance(Long id);
}
