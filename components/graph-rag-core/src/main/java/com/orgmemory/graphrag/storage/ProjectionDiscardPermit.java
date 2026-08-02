package com.orgmemory.graphrag.storage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Store-issued proof that one exact attempt can no longer become visible. */
public record ProjectionDiscardPermit(
        UUID id,
        UUID batchId,
        Instant issuedAt) {

    public ProjectionDiscardPermit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(issuedAt, "issuedAt");
    }

    public void requireAuthorizes(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        if (!batchId.equals(batch.id())) {
            throw new ProjectionPublicationStore.PublicationConflictException(
                    "discard permit does not authorize this publication attempt");
        }
    }
}
