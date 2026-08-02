package com.orgmemory.graphrag.storage;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * One immutable publication attempt across every required derived projection.
 */
public record ProjectionBatch(
        UUID id,
        ProjectionNamespace namespace,
        UUID expectedPreviousBatchId,
        long expectedPreviousGeneration,
        long generation,
        String idempotencyKey,
        String manifestFingerprint,
        Set<ProjectionKind> requiredProjections,
        long claimEpoch,
        Instant createdAt) {

    public ProjectionBatch(
            UUID id,
            ProjectionNamespace namespace,
            long expectedPreviousGeneration,
            long generation,
            String idempotencyKey,
            String manifestFingerprint,
            Set<ProjectionKind> requiredProjections,
            Instant createdAt) {
        this(
                id,
                namespace,
                null,
                expectedPreviousGeneration,
                generation,
                idempotencyKey,
                manifestFingerprint,
                requiredProjections,
                0,
                createdAt);
    }

    public ProjectionBatch {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(namespace, "namespace");
        if (expectedPreviousGeneration == 0 && expectedPreviousBatchId != null) {
            throw new IllegalArgumentException(
                    "the empty predecessor cannot have a batch id");
        }
        if (expectedPreviousGeneration > 0
                && expectedPreviousBatchId == null
                && claimEpoch > 0) {
            throw new IllegalArgumentException(
                    "a production publication must pin the predecessor batch id");
        }
        if (expectedPreviousGeneration < 0) {
            throw new IllegalArgumentException(
                    "expectedPreviousGeneration must be non-negative");
        }
        if (expectedPreviousGeneration == Long.MAX_VALUE
                || generation != expectedPreviousGeneration + 1) {
            throw new IllegalArgumentException(
                    "generation must immediately follow expectedPreviousGeneration");
        }
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        manifestFingerprint = requireText(manifestFingerprint, "manifestFingerprint");
        requiredProjections =
                Set.copyOf(Objects.requireNonNull(requiredProjections, "requiredProjections"));
        if (requiredProjections.isEmpty()) {
            throw new IllegalArgumentException("requiredProjections must not be empty");
        }
        if (claimEpoch < 0) {
            throw new IllegalArgumentException("claimEpoch must be non-negative");
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
