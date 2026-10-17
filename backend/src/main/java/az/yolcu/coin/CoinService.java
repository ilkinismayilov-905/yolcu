package az.yolcu.coin;

import az.yolcu.common.AppClock;
import az.yolcu.stream.StreamEvent;
import az.yolcu.user.UserRepository;
import java.util.Collection;
import java.util.Map;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coin rules (02_ARCHITECTURE §9). The only place where users.coin_balance changes. */
@Service
public class CoinService {
    private final CoinTxRepository txs;
    private final UserRepository users;
    private final ApplicationEventPublisher events;
    private final AppClock clock;

    public CoinService(CoinTxRepository txs, UserRepository users, ApplicationEventPublisher events, AppClock clock) {
        this.txs = txs;
        this.users = users;
        this.events = events;
        this.clock = clock;
    }

    /** Adds a ledger row and updates the balance; returns the awarded amount, 0 if (user, reason, refId) was already awarded. */
    @Transactional
    public int award(long userId, int amount, String reason, String refId, String label) {
        // unique(user_id, reason, ref_id) in coin_tx is the backstop for concurrent duplicates
        if (txs.existsByUserIdAndReasonAndRefId(userId, reason, refId)) return 0;
        txs.save(new CoinTx(userId, amount, reason, refId, label, clock.now()));
        users.addCoins(userId, amount);
        events.publishEvent(StreamEvent.toUser(userId, "coin.updated", Map.of("balance", users.coinBalance(userId))));
        return amount;
    }

    @Transactional(readOnly = true)
    public int sumFor(long userId, Collection<String> refIds) {
        return txs.sumByUserIdAndRefIdIn(userId, refIds);
    }
}
