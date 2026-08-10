package com.orgmemory.graphrag.parsing;

import java.util.Objects;

/** A deterministic failure to turn supplied bytes into canonical evidence. */
public final class DocumentParseException extends RuntimeException {

    private final String code;

    public DocumentParseException(String code, String message) {
        super(message);
        this.code = requireText(code, "code");
    }

    public DocumentParseException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = requireText(code, "code");
    }

    public String code() {
        return code;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
