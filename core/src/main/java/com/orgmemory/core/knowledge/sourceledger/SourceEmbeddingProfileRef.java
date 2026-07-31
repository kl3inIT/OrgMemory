package com.orgmemory.core.knowledge.sourceledger;

import java.util.UUID;

/** Stable embedding facts persisted with a completed source revision. */
public record SourceEmbeddingProfileRef(UUID id, int dimensions) {

    public SourceEmbeddingProfileRef {
        if (id == null) {
            throw new IllegalArgumentException("source embedding profile id is required");
        }
        if (dimensions < 1) {
            throw new IllegalArgumentException("source embedding dimensions must be positive");
        }
    }
}
