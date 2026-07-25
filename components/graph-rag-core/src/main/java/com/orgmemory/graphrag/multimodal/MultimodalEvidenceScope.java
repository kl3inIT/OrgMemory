package com.orgmemory.graphrag.multimodal;

import java.util.Objects;
import java.util.UUID;

/** Security and lineage identity inherited by every derived multimodal record. */
public record MultimodalEvidenceScope(
        UUID organizationId,
        UUID sourceRevisionId,
        UUID aclSnapshotId,
        long aclGeneration) {

    public MultimodalEvidenceScope {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(aclSnapshotId, "aclSnapshotId");
        if (aclGeneration < 0) {
            throw new IllegalArgumentException("aclGeneration must be non-negative");
        }
    }
}
