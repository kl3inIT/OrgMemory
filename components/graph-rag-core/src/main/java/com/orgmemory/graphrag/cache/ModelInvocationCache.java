package com.orgmemory.graphrag.cache;

import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.Map;
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

    /**
     * Atomically persists entries and enforces the row bound for one namespace
     * and operation. Implementations without transactional bounded persistence
     * must reject this operation so callers can safely skip caching.
     */
    default void putBounded(
            ProjectionNamespace namespace,
            String operation,
            Map<Key, Entry> entries,
            Instant now,
            int maximumEntries) {
        Objects.requireNonNull(namespace, "namespace");
        requireText(operation, "operation");
        Map<Key, Entry> validated = Map.copyOf(Objects.requireNonNull(entries, "entries"));
        Objects.requireNonNull(now, "now");
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
        validated.forEach((key, entry) -> {
            if (!key.namespace().equals(namespace) || !key.operation().equals(operation)) {
                throw new IllegalArgumentException(
                        "bounded entries must match namespace and operation");
            }
            Objects.requireNonNull(entry, "entry");
        });
        throw new UnsupportedOperationException("bounded cache persistence is unavailable");
    }

    /** Deletes at most {@code maximumRows} expired rows for one operation. */
    default int deleteExpired(String operation, Instant now, int maximumRows) {
        requireText(operation, "operation");
        Objects.requireNonNull(now, "now");
        if (maximumRows <= 0) {
            throw new IllegalArgumentException("maximumRows must be positive");
        }
        return 0;
    }

    /**
     * Best-effort bounded maintenance for one namespace and operation.
     * Prefer {@link #putBounded} when persistence and bounding must be atomic.
     */
    default void prune(
            ProjectionNamespace namespace,
            String operation,
            Instant now,
            int maximumEntries) {
        Objects.requireNonNull(namespace, "namespace");
        requireText(operation, "operation");
        Objects.requireNonNull(now, "now");
        if (maximumEntries <= 0) {
            throw new IllegalArgumentException("maximumEntries must be positive");
        }
    }

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
