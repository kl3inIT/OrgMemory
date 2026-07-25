package com.orgmemory.graphrag.multimodal;

import java.util.List;
import java.util.Objects;

/** Deterministically reconstructed context around one sidecar target. */
public record MultimodalSurroundingContext(
        List<String> headingPath,
        String before,
        String after,
        String caption,
        String footnotes,
        String builderVersion) {

    public MultimodalSurroundingContext {
        headingPath = List.copyOf(Objects.requireNonNull(headingPath, "headingPath"));
        before = Objects.requireNonNull(before, "before");
        after = Objects.requireNonNull(after, "after");
        caption = Objects.requireNonNull(caption, "caption");
        footnotes = Objects.requireNonNull(footnotes, "footnotes");
        builderVersion = requireText(builderVersion, "builderVersion");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
