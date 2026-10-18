package az.yolcu.survey.dto;

import java.time.OffsetDateTime;

/** Row of GET /admin/surveys; also the `survey.updated` SSE payload. responses = historic seed total + real answers. */
public record SurveyListItem(Long id, String title, String status, String audience, String audienceLabel, long responses,
                             int coinReward, OffsetDateTime createdAt, OffsetDateTime publishedAt,
                             OffsetDateTime closedAt) {}
