package az.yolcu.coin.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** GET /me/wallet. history: newest first, no SEED/DEMO rows; status only on CONVERSION rows (PENDING | COMPLETED). */
public record Wallet(int balance, String aznEquivalent, int rate, int earnedToday, List<Tx> history, List<Rule> rules) {
    public record Tx(String label, int amount, OffsetDateTime createdAt, String status) {}

    public record Rule(String label, String amount) {}
}
