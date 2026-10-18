package az.yolcu.coin.dto;

/** POST /me/wallet/conversions response. */
public record ConversionCreated(long id, int coins, String azn, String status, int coinBalance) {}
