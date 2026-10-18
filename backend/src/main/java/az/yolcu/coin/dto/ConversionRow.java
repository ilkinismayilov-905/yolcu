package az.yolcu.coin.dto;

import java.time.OffsetDateTime;

/** GET /admin/coins/conversions item and conversion.updated SSE payload. */
public record ConversionRow(long id, String userName, String phoneMask, int coins, String azn, String cardMask, String status,
                            OffsetDateTime createdAt, OffsetDateTime completedAt) {}
