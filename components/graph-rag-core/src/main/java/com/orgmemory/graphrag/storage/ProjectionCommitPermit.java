package com.orgmemory.graphrag.storage;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Irrevocable authority to finalize one exact immutable publication attempt. */
public record ProjectionCommitPermit(
        UUID id,
        UUID batchId,
        String manifestFingerprint,
        long claimEpoch,
        Instant issuedAt) {

    public ProjectionCommitPermit {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(batchId, "batchId");
        manifestFingerprint = requireText(manifestFingerprint, "manifestFingerprint");
        if (claimEpoch < 0) {
            throw new IllegalArgumentException("claimEpoch must be non-negative");
        }
        Objects.requireNonNull(issuedAt, "issuedAt");
    }

    public void requireAuthorizes(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        if (!batchId.equals(batch.id())
                || !manifestFingerprint.equals(batch.manifestFingerprint())
                || (batch.claimEpoch() > 0 && claimEpoch != batch.claimEpoch())) {
            throw new ProjectionPublicationStore.PublicationConflictException(
                    "commit permit does not authorize this publication attempt");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
