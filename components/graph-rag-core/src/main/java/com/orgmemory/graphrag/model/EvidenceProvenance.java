package com.orgmemory.graphrag.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EvidenceProvenance(
        UUID organizationId,
        UUID knowledgeAssetId,
        UUID sourceRevisionId,
        UUID chunkId,
        UUID aclSnapshotId,
        long aclGeneration,
        long projectionGeneration,
        String extractorProvider,
        String extractorModel,
        String promptVersion,
        double confidence,
        Instant extractedAt) {

    public EvidenceProvenance {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(aclSnapshotId, "aclSnapshotId");
        extractorProvider = requireText(extractorProvider, "extractorProvider");
        extractorModel = requireText(extractorModel, "extractorModel");
        promptVersion = requireText(promptVersion, "promptVersion");
        Objects.requireNonNull(extractedAt, "extractedAt");
        if (aclGeneration < 0) {
            throw new IllegalArgumentException("aclGeneration must be non-negative");
        }
        if (projectionGeneration < 0) {
            throw new IllegalArgumentException("projectionGeneration must be non-negative");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
