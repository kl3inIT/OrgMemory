package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;
import java.util.UUID;

/** Space facts needed to register and classify source evidence. */
public record SourceKnowledgeSpaceRef(
        UUID id,
        String key,
        String name,
        UUID departmentId) {

    public SourceKnowledgeSpaceRef {
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
