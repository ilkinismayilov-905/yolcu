package az.yolcu.coin.dto;

/** POST /me/wallet/conversions body; validated in ConversionService for contract messages. */
public record ConversionRequest(Integer coins, String cardMask) {}
