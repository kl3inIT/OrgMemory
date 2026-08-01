package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;
import java.util.UUID;

/** Immutable source-revision state required by graph indexing. */
public record SourceGraphIndexRevisionRef(
        UUID id,
        UUID embeddingProfileId,
        UUID knowledgeAssetId,
        UUID knowledgeAssetVersionId,
        boolean ready) {

    public SourceGraphIndexRevisionRef {
        Objects.requireNonNull(id, "id");
    }
}
