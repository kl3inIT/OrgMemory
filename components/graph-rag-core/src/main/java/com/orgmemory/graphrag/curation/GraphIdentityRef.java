package com.orgmemory.graphrag.curation;

import java.util.Objects;
import java.util.UUID;

public record GraphIdentityRef(GraphIdentityKind kind, UUID id) {

    public GraphIdentityRef {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(id, "id");
    }

    public static GraphIdentityRef entity(UUID id) {
        return new GraphIdentityRef(GraphIdentityKind.ENTITY, id);
    }

    public static GraphIdentityRef relation(UUID id) {
        return new GraphIdentityRef(GraphIdentityKind.RELATION, id);
    }
}
