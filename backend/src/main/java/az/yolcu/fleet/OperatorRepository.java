package az.yolcu.fleet;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OperatorRepository extends JpaRepository<Operator, Long> {
    @Query(value = "select operator_id from buses where bort = :bort", nativeQuery = true)
    Optional<Long> findIdByBort(String bort);
}
