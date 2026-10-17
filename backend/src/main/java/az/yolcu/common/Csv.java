package az.yolcu.common;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Excel-friendly CSV: ';' separator, CRLF, RFC 4180 quoting, formula-injection guard. */
public final class Csv {
    public static final String BOM = "﻿";
    public static final String CRLF = "\r\n";

    private Csv() {}

    public static String row(String... fields) {
        return Arrays.stream(fields).map(Csv::field).collect(Collectors.joining(";")) + CRLF;
    }

    public static String field(String v) {
        if (v == null || v.isEmpty()) return "";
        // cells starting with these are evaluated as formulas by Excel/Sheets; text comes from passengers
        if ("=+-@\t\r".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        boolean quote = v.chars().anyMatch(c -> c == ';' || c == ',' || c == '"' || c == '\r' || c == '\n');
        return quote ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }
}
