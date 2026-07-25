package com.orgmemory.graphrag.indexing;

import java.util.List;
import java.util.Objects;

/**
 * Byte-stable entity and relation embedding payloads from pinned LightRAG
 * v1.5.4.
 *
 * <p>Contribution types remain metadata. Adding them to these strings changes
 * vector semantics and therefore requires an explicitly versioned graph
 * processing profile and a new projection publication.
 */
public final class LightRagEmbeddingPayloads {

    public static final String FORMAT_ID = "lightrag-v1.5.4";

    private LightRagEmbeddingPayloads() {
    }

    public static String entity(String normalizedName, String description) {
        return required(normalizedName, "normalizedName")
                + "\n"
                + required(description, "description");
    }

    public static String relation(
            List<String> keywords,
            String sourceName,
            String targetName,
            String description) {
        Objects.requireNonNull(keywords, "keywords");
        String keywordText = String.join(", ", keywords.stream()
                .map(keyword -> required(keyword, "keyword"))
                .toList());
        return keywordText
                + "\t"
                + required(sourceName, "sourceName")
                + "\n"
                + required(targetName, "targetName")
                + "\n"
                + required(description, "description");
    }

    private static String required(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
