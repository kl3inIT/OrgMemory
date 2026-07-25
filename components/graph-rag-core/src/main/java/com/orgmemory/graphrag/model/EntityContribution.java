package com.orgmemory.graphrag.model;

import java.util.Objects;
import java.util.UUID;

public record EntityContribution(
        UUID id,
        CanonicalEntity entity,
        String type,
        String description,
        EvidenceProvenance provenance) {

    public EntityContribution {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entity, "entity");
        type = requireText(type, "type");
        description = requireText(description, "description");
        Objects.requireNonNull(provenance, "provenance");
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
