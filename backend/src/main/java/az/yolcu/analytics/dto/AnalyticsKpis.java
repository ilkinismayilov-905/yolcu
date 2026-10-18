package az.yolcu.analytics.dto;

public record AnalyticsKpis(long trackedTrips30d, long consentUsers, int avgTripMin, int longestTransferWaitMin,
                            int suggestionAcceptance) {}
