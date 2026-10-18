package az.yolcu.fleet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** GET /admin/search: type BUS | REPORT | NONE; ref omitted for NONE. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SearchResult(String type, String ref) {}
