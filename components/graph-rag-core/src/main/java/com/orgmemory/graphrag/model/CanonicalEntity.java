package com.orgmemory.graphrag.model;

import java.util.Objects;
import java.util.UUID;

public record CanonicalEntity(UUID id, String normalizedName, String type) {

    public CanonicalEntity {
        Objects.requireNonNull(id, "id");
        normalizedName = requireText(normalizedName, "normalizedName");
        type = requireText(type, "type");
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
