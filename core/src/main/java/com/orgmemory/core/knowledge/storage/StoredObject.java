package com.orgmemory.core.knowledge.storage;

public record StoredObject(
        ObjectKey key,
        long contentLength,
        String mediaType,
        String sha256,
        String etag,
        String storageVersion) {
}
