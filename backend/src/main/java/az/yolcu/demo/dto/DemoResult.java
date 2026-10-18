package az.yolcu.demo.dto;

/** POST /demo/trigger/{scenario} response. */
public record DemoResult(boolean ok, String scenario, String message) {}
