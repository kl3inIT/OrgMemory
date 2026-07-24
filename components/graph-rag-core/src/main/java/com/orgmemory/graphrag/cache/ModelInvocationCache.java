package com.orgmemory.graphrag.cache;

import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact cache for deterministic model-derived artifacts such as keyword or
 * extraction output. It is separate from authorization-scoped retrieval
 * results and final-answer semantic caches.
 */
public interface ModelInvocationCache {

    Optional<Entry> get(Key key, Instant now);

    void put(Key key, Entry entry);

    void invalidate(ProjectionNamespace namespace);

    record Key(
            ProjectionNamespace namespace,
            String operation,
            String inputHash,
            String modelRouteFingerprint,
            String profileFingerprint) {

        public Key {
            Objects.requireNonNull(namespace, "namespace");
            operation = requireText(operation, "operation");
            inputHash =
                    CanonicalCacheKeyHasher.requireSha256(inputHash, "inputHash");
            modelRouteFingerprint =
                    requireText(modelRouteFingerprint, "modelRouteFingerprint");
            profileFingerprint = requireText(profileFingerprint, "profileFingerprint");
        }
    }

    record Entry(String mediaType, String payload, Instant createdAt, Instant expiresAt) {

        public Entry {
            mediaType = requireText(mediaType, "mediaType");
            payload = Objects.requireNonNull(payload, "payload");
            Objects.requireNonNull(createdAt, "createdAt");
            Objects.requireNonNull(expiresAt, "expiresAt");
            if (!expiresAt.isAfter(createdAt)) {
                throw new IllegalArgumentException("expiresAt must be after createdAt");
            }
        }

        public boolean expiredAt(Instant instant) {
            return !Objects.requireNonNull(instant, "instant").isBefore(expiresAt);
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
