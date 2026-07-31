package com.orgmemory.core.shared;

import java.util.Objects;

public final class Texts {

    private Texts() {
    }

    public static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    public static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
