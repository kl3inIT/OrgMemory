package com.orgmemory.graphrag.multimodal;

import java.util.Objects;

/** Searchable text derived from one successful analysis with materialized provenance. */
public record MultimodalDerivedChunk(
        String itemId,
        MultimodalModality modality,
        String content,
        MultimodalEvidenceScope evidenceScope,
        int sourceBlockIndex,
        int sourceStartChar,
        int sourceEndChar,
        String artifactContentSha256,
        MultimodalAnalysisRoute route,
        MultimodalAnalysisCacheKey analysisCacheKey) {

    public MultimodalDerivedChunk {
        itemId = requireText(itemId, "itemId");
        Objects.requireNonNull(modality, "modality");
        content = requireText(content, "content");
        Objects.requireNonNull(evidenceScope, "evidenceScope");
        if (sourceBlockIndex < 0 || sourceStartChar < 0 || sourceEndChar <= sourceStartChar) {
            throw new IllegalArgumentException("derived chunk source span is invalid");
        }
        artifactContentSha256 = requireSha256(artifactContentSha256);
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(analysisCacheKey, "analysisCacheKey");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "artifactContentSha256").toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "artifactContentSha256 must be a lowercase SHA-256");
        }
        return normalized;
    }
}
