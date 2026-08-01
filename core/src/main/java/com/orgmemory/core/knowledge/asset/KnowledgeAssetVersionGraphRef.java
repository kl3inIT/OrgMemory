package com.orgmemory.core.knowledge.asset;

import java.util.Objects;
import java.util.UUID;

/** Immutable Knowledge Asset version state required by graph consumers. */
public record KnowledgeAssetVersionGraphRef(
        UUID id,
        UUID knowledgeAssetId,
        UUID sourceRevisionId,
        UUID sourceAclSnapshotId,
        long versionNumber,
        String language,
        boolean active) {

    public KnowledgeAssetVersionGraphRef {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
    }
}
