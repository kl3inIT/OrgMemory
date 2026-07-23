package com.orgmemory.graphrag.model;

import java.util.Objects;
import java.util.UUID;

public record CanonicalRelation(
        UUID id,
        UUID sourceEntityId,
        UUID targetEntityId,
        String type,
        RelationOrientation orientation) {

    public CanonicalRelation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        Objects.requireNonNull(targetEntityId, "targetEntityId");
        Objects.requireNonNull(orientation, "orientation");
        type = requireText(type, "type");
        if (sourceEntityId.equals(targetEntityId)) {
            throw new IllegalArgumentException("A relation must connect two different entities");
        }
        if (orientation == RelationOrientation.UNDIRECTED
                && sourceEntityId.compareTo(targetEntityId) > 0) {
            UUID previousSource = sourceEntityId;
            sourceEntityId = targetEntityId;
            targetEntityId = previousSource;
        }
    }

    public boolean isIncidentTo(UUID entityId) {
        return sourceEntityId.equals(entityId) || targetEntityId.equals(entityId);
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
