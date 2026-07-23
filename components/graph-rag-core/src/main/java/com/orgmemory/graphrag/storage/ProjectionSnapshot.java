package com.orgmemory.graphrag.storage;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Query-visible generation. Readers must pin every projection read to this
 * exact snapshot.
 */
public record ProjectionSnapshot(
        UUID batchId,
        ProjectionNamespace namespace,
        long generation,
        String manifestFingerprint,
        Set<ProjectionKind> projections,
        Instant publishedAt) {

    public ProjectionSnapshot {
        Objects.requireNonNull(batchId, "batchId");
        Objects.requireNonNull(namespace, "namespace");
        if (generation <= 0) {
            throw new IllegalArgumentException("generation must be positive");
        }
        manifestFingerprint = requireText(manifestFingerprint, "manifestFingerprint");
        projections = Set.copyOf(Objects.requireNonNull(projections, "projections"));
        if (projections.isEmpty()) {
            throw new IllegalArgumentException("projections must not be empty");
        }
        Objects.requireNonNull(publishedAt, "publishedAt");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
