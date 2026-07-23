package com.orgmemory.graphrag.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EvidenceProvenance(
        EvidenceReference evidence,
        long projectionGeneration,
        String extractorProvider,
        String extractorModel,
        String promptVersion,
        double confidence,
        Instant extractedAt) {

    public EvidenceProvenance {
        Objects.requireNonNull(evidence, "evidence");
        extractorProvider = requireText(extractorProvider, "extractorProvider");
        extractorModel = requireText(extractorModel, "extractorModel");
        promptVersion = requireText(promptVersion, "promptVersion");
        Objects.requireNonNull(extractedAt, "extractedAt");
        if (projectionGeneration < 0) {
            throw new IllegalArgumentException("projectionGeneration must be non-negative");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }

    public UUID organizationId() {
        return evidence.organizationId();
    }

    public UUID knowledgeAssetId() {
        return evidence.knowledgeAssetId();
    }

    public UUID sourceRevisionId() {
        return evidence.sourceRevisionId();
    }

    public UUID chunkId() {
        return evidence.chunkId();
    }

    public UUID aclSnapshotId() {
        return evidence.aclSnapshotId();
    }

    public long aclGeneration() {
        return evidence.aclGeneration();
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
