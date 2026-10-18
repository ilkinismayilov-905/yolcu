package az.yolcu.demo.dto;

import java.util.Map;

/** POST /demo/reset response: deleted = table → rows removed. */
public record ResetResult(boolean ok, String scenario, String message, int coinBalance, Map<String, Integer> deleted) {}
