package com.orgmemory.graphrag.parsing;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One typed block with offsets into the canonical document text. */
public record DocumentBlock(
        int index,
        DocumentBlockKind kind,
        int startChar,
        int endChar,
        Integer startPage,
        Integer endPage,
        Integer headingLevel,
        Map<String, String> attributes) {

    public DocumentBlock {
        if (index < 0) {
            throw new IllegalArgumentException("block index must not be negative");
        }
        Objects.requireNonNull(kind, "kind");
        if (startChar < 0 || endChar <= startChar) {
            throw new IllegalArgumentException("block source span must be non-empty");
        }
        if (startPage != null && startPage <= 0
                || endPage != null && endPage <= 0
                || startPage != null && endPage != null && endPage < startPage) {
            throw new IllegalArgumentException("block page range is invalid");
        }
        if (headingLevel != null && (headingLevel < 1 || headingLevel > 6)) {
            throw new IllegalArgumentException("heading level must be between 1 and 6");
        }
        if (kind == DocumentBlockKind.HEADING && headingLevel == null) {
            throw new IllegalArgumentException("heading blocks require a heading level");
        }
        attributes = Map.copyOf(new TreeMap<>(Objects.requireNonNull(attributes, "attributes")));
    }
}
