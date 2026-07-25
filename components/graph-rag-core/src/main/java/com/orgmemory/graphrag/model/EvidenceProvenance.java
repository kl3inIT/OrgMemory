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
        String extractionProfileFingerprint,
        double confidence,
        Instant extractedAt) {

    public EvidenceProvenance {
        Objects.requireNonNull(evidence, "evidence");
        extractorProvider = requireText(extractorProvider, "extractorProvider");
        extractorModel = requireText(extractorModel, "extractorModel");
        promptVersion = requireText(promptVersion, "promptVersion");
        extractionProfileFingerprint =
                requireFingerprint(extractionProfileFingerprint);
        Objects.requireNonNull(extractedAt, "extractedAt");
        if (projectionGeneration < 0) {
            throw new IllegalArgumentException("projectionGeneration must be non-negative");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }

    public EvidenceProvenance(
            EvidenceReference evidence,
            long projectionGeneration,
            String extractorProvider,
            String extractorModel,
            String promptVersion,
            double confidence,
            Instant extractedAt) {
        this(
                evidence,
                projectionGeneration,
                extractorProvider,
                extractorModel,
                promptVersion,
                "0000000000000000000000000000000000000000000000000000000000000000",
                confidence,
                extractedAt);
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

    private static String requireFingerprint(String value) {
        String normalized = requireText(value, "extractionProfileFingerprint");
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "extractionProfileFingerprint must be lowercase SHA-256 hex");
        }
        return normalized;
    }
}
