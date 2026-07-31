package com.orgmemory.core.knowledge.sourceledger;

import java.util.UUID;

/** Source-list projection of retrieval-owned embedding profile metadata. */
public record SourceEmbeddingProfileView(
        UUID id,
        String profileKey,
        String provider,
        String model,
        int dimensions) {
}
