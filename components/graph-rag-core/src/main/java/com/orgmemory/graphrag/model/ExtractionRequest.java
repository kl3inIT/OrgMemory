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
        String sectionContext,
        Locale language,
        ExtractionProfile profile) {

    public ExtractionRequest {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(chunkId, "chunkId");
        content = requireText(content, "content");
        sectionContext = sectionContext == null || sectionContext.isBlank()
                ? null
                : sectionContext.strip();
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(profile, "profile");
    }

    public ExtractionRequest(
            UUID organizationId,
            UUID knowledgeAssetId,
            UUID sourceRevisionId,
            UUID chunkId,
            String content,
            Locale language,
            ExtractionProfile profile) {
        this(
                organizationId,
                knowledgeAssetId,
                sourceRevisionId,
                chunkId,
                content,
                null,
                language,
                profile);
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
