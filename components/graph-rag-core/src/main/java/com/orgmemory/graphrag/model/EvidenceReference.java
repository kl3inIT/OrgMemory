package com.orgmemory.graphrag.model;

import java.util.Objects;
import java.util.UUID;

/**
 * Canonical identity of the source evidence behind a derived record.
 *
 * <p>A source revision is already pinned to an immutable Knowledge Asset
 * version by the OrgMemory ledger, so the version is not duplicated here.
 */
public record EvidenceReference(
        UUID organizationId,
        UUID knowledgeAssetId,
        UUID sourceRevisionId,
        UUID chunkId,
        UUID aclSnapshotId,
        long aclGeneration) {

    public EvidenceReference {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(aclSnapshotId, "aclSnapshotId");
        if (aclGeneration < 0) {
            throw new IllegalArgumentException("aclGeneration must be non-negative");
        }
    }

    public boolean isChunk() {
        return chunkId != null;
    }
}
