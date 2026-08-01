package com.orgmemory.core.knowledge.asset;

import java.util.Objects;
import java.util.UUID;

/** Immutable Knowledge Asset state required by graph consumers. */
public record KnowledgeAssetGraphRef(
        UUID id,
        UUID knowledgeSpaceId,
        UUID currentVersionId,
        boolean archived) {

    public KnowledgeAssetGraphRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
    }
}
