package com.orgmemory.core.knowledge;

import java.util.Objects;
import java.util.UUID;

public record KnowledgeSpaceTarget(
        UUID id,
        String key,
        String name,
        UUID departmentId) {

    public KnowledgeSpaceTarget {
        Objects.requireNonNull(id, "id");
        key = requireText(key, "key");
        name = requireText(name, "name");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
