package com.orgmemory.graphrag.model;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record ExtractionRequest(
        UUID organizationId,
        UUID knowledgeAssetId,
        UUID sourceRevisionId,
        UUID chunkId,
        String content,
        Locale language,
        ExtractionProfile profile) {

    public ExtractionRequest {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(chunkId, "chunkId");
        content = requireText(content, "content");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(profile, "profile");
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
