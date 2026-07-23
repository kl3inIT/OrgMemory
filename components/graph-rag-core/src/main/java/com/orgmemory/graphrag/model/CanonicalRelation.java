package com.orgmemory.graphrag.model;

import java.util.Objects;
import java.util.UUID;

public record CanonicalRelation(
        UUID id,
        UUID sourceEntityId,
        UUID targetEntityId,
        RelationOrientation orientation) {

    public CanonicalRelation {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(sourceEntityId, "sourceEntityId");
        Objects.requireNonNull(targetEntityId, "targetEntityId");
        Objects.requireNonNull(orientation, "orientation");
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

}
