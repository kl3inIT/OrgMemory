package com.orgmemory.core.knowledge.storage;

import java.util.Map;

public record ObjectWriteRequest(
        ObjectKey key,
        long contentLength,
        String mediaType,
        Map<String, String> metadata) {

    public ObjectWriteRequest {
        if (key == null) {
            throw new IllegalArgumentException("object key is required");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("content length cannot be negative");
        }
        mediaType = mediaType == null || mediaType.isBlank()
                ? "application/octet-stream"
                : mediaType.trim();
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
