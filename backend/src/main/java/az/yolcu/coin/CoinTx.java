package az.yolcu.coin;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "coin_tx")
public class CoinTx {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private int amount;
    private String reason;
    private String refId;
    private String label;
    private OffsetDateTime createdAt;

    protected CoinTx() {}

    public CoinTx(Long userId, int amount, String reason, String refId, String label, OffsetDateTime createdAt) {
        this.userId = userId;
        this.amount = amount;
        this.reason = reason;
        this.refId = refId;
        this.label = label;
        this.createdAt = createdAt;
    }

    public int getAmount() { return amount; }
}
