package com.orgmemory.graphrag.parsing;

import com.orgmemory.graphrag.multimodal.MultimodalSidecar;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public record DocumentParseResult(
        CanonicalDocument document,
        String detectedMediaType,
        Map<String, String> metadata,
        Optional<MultimodalSidecar> multimodalSidecar) {

    public DocumentParseResult {
        Objects.requireNonNull(document, "document");
        detectedMediaType = Objects.requireNonNull(detectedMediaType, "detectedMediaType").trim();
        if (detectedMediaType.isEmpty()) {
            throw new IllegalArgumentException("detectedMediaType must not be blank");
        }
        metadata = Map.copyOf(new TreeMap<>(Objects.requireNonNull(metadata, "metadata")));
        multimodalSidecar =
                Objects.requireNonNull(multimodalSidecar, "multimodalSidecar");
        multimodalSidecar.ifPresent(sidecar -> {
            if (!sidecar.canonicalTextSha256().equals(document.contentSha256())) {
                throw new IllegalArgumentException(
                        "multimodal sidecar must reference the parsed canonical text");
            }
        });
    }

    public DocumentParseResult(
            CanonicalDocument document,
            String detectedMediaType,
            Map<String, String> metadata) {
        this(document, detectedMediaType, metadata, Optional.empty());
    }
}
