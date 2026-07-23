package com.orgmemory.graphrag.parsing;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record DocumentParseResult(
        CanonicalDocument document,
        String detectedMediaType,
        Map<String, String> metadata) {

    public DocumentParseResult {
        Objects.requireNonNull(document, "document");
        detectedMediaType = Objects.requireNonNull(detectedMediaType, "detectedMediaType").trim();
        if (detectedMediaType.isEmpty()) {
            throw new IllegalArgumentException("detectedMediaType must not be blank");
        }
        metadata = Map.copyOf(new TreeMap<>(Objects.requireNonNull(metadata, "metadata")));
    }
}
