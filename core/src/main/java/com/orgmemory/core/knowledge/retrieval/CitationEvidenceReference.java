package com.orgmemory.core.knowledge.retrieval;

import java.util.Objects;
import java.util.UUID;

/** Currently authorized metadata for one replayed citation. */
public record CitationEvidenceReference(
        UUID chunkId,
        String title,
        String heading,
        Integer startPage,
        Integer endPage) {

    public CitationEvidenceReference {
        Objects.requireNonNull(chunkId, "chunkId");
        title = normalize(title, "Company knowledge");
        heading = normalize(heading, null);
    }

    private static String normalize(String value, String fallback) {
        String normalized = value == null ? "" : value.strip();
        return normalized.isEmpty() ? fallback : normalized;
    }
}
