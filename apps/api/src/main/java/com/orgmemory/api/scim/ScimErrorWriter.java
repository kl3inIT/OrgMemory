package com.orgmemory.api.scim;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class ScimErrorWriter {

    static final String MEDIA_TYPE = "application/scim+json";
    static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

    private ScimErrorWriter() {
    }

    static void write(HttpServletResponse response, int status, String detail)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MEDIA_TYPE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"schemas":["%s"],"status":"%d","detail":"%s"}
                """.formatted(ERROR_SCHEMA, status, escape(detail)));
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
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
                        escaped.append("\\u");
                        escaped.append(Character.forDigit((character >> 12) & 0xf, 16));
                        escaped.append(Character.forDigit((character >> 8) & 0xf, 16));
                        escaped.append(Character.forDigit((character >> 4) & 0xf, 16));
                        escaped.append(Character.forDigit(character & 0xf, 16));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
