package az.yolcu.coin.dto;

/** GET /admin/coins/rules item; amount is a display string with sign ("+5", "+5–20", "−60"). */
public record CoinRule(String code, String action, String amount, String limit) {}
