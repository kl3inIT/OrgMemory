package com.orgmemory.graphrag.multimodal;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * Opaque reference to parser-produced binary evidence.
 *
 * <p>The reference is resolved by an integration adapter; storage paths and object keys never
 * cross the core boundary.
 */
public record MultimodalBinaryArtifact(
        String artifactId,
        String mediaType,
        long byteSize,
        String contentSha256,
        OptionalInt width,
        OptionalInt height) {

    public MultimodalBinaryArtifact {
        artifactId = requireText(artifactId, "artifactId");
        mediaType = requireText(mediaType, "mediaType").toLowerCase();
        if (byteSize < 0) {
            throw new IllegalArgumentException("byteSize must be non-negative");
        }
        contentSha256 = requireSha256(contentSha256);
        width = Objects.requireNonNull(width, "width");
        height = Objects.requireNonNull(height, "height");
        if (width.isPresent() != height.isPresent()
                || width.isPresent() && (width.getAsInt() <= 0 || height.getAsInt() <= 0)) {
            throw new IllegalArgumentException(
                    "artifact dimensions must both be present and positive");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "contentSha256").toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "contentSha256 must be a lowercase SHA-256");
        }
        return normalized;
    }
}
