package az.yolcu.coin;

import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CoinTxRepository extends JpaRepository<CoinTx, Long> {
    boolean existsByUserIdAndReasonAndRefId(Long userId, String reason, String refId);

    @Query("select coalesce(sum(t.amount), 0) from CoinTx t where t.userId = :userId and t.refId in :refIds")
    int sumByUserIdAndRefIdIn(Long userId, Collection<String> refIds);
}
