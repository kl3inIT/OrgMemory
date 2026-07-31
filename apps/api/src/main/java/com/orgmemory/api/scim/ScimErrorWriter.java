package com.orgmemory.api.scim;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import tools.jackson.core.io.JsonStringEncoder;

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
        JsonStringEncoder.getInstance().quoteAsString(value, escaped);
        return escaped.toString();
    }
}
