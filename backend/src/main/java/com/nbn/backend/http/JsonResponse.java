package com.nbn.backend.http;

public final class JsonResponse {
    private JsonResponse() {
    }

    public static String ok(String requestId, String dataJson) {
        return envelope(requestId, ErrorCode.OK, "ok", dataJson);
    }

    public static String error(String requestId, ErrorCode code, String message) {
        return envelope(requestId, code, message, "null");
    }

    public static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String envelope(
            String requestId,
            ErrorCode code,
            String message,
            String dataJson
    ) {
        return "{"
                + "\"requestId\":\"" + escape(requestId) + "\","
                + "\"code\":\"" + code.name() + "\","
                + "\"message\":\"" + escape(message) + "\","
                + "\"data\":" + dataJson
                + "}";
    }
}
