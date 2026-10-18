package az.yolcu.coin.dto;

/** GET /admin/coins/summary. */
public record CoinSummary(int earnedToday, int earnedTodayUsers, long inCirculation, String convertedThisMonthAzn, int rate) {}
