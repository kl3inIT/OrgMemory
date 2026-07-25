package com.orgmemory.graphrag.validation;

import java.util.Objects;

/** Shared validation for required, normalized graph-RAG text fields. */
public final class TextValidation {

    private TextValidation() {
    }

    public static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
