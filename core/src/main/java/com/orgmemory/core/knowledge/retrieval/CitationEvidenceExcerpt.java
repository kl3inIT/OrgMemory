package com.orgmemory.core.knowledge.retrieval;

import java.util.Objects;

/** Bounded, currently authorized citation evidence for browser presentation. */
public record CitationEvidenceExcerpt(
        String title,
        String heading,
        Integer startPage,
        Integer endPage,
        String excerpt,
        boolean truncated,
        CitationPresentationKind presentationKind) {

    public CitationEvidenceExcerpt {
        title = requireText(title, "title");
        heading = optionalText(heading);
        excerpt = requireText(excerpt, "excerpt");
        Objects.requireNonNull(presentationKind, "presentationKind");
    }

    private static String requireText(String value, String field) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String optionalText(String value) {
        String normalized = value == null ? "" : value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
