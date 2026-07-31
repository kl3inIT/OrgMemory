package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;
import java.util.UUID;

/** Space facts needed to register and classify source evidence. */
public record SourceKnowledgeSpaceRef(UUID id, UUID departmentId) {

    public SourceKnowledgeSpaceRef {
        Objects.requireNonNull(id, "id");
    }
}
