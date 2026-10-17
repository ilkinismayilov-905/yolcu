package az.yolcu.sim.dto;

/** hub.counts SSE item (02_ARCHITECTURE §4). */
public record HubCount(String hubId, int count, int trend, int forecast15) {}
