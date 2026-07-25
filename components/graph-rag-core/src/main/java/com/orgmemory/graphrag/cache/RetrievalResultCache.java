package com.orgmemory.graphrag.cache;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Exact permission-scoped cache for retrieval output.
 *
 * <p>The key factory binds authorization and publication generations. Cache
 * hits are still derived data: callers must recheck every citation against
 * canonical authorization before returning it.
 */
public interface RetrievalResultCache {

    Optional<Entry> get(Key key, Instant now);

    void put(Key key, Entry entry);

    void invalidateNamespace(ProjectionNamespace namespace);

    static void requireValidEntry(Key key, Entry entry) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(entry, "entry");
        var organizationId = key.snapshot().namespace().organizationId();
        if (entry.evidence().stream()
                .anyMatch(evidence ->
                        !organizationId.equals(evidence.organizationId()))) {
            throw new IllegalArgumentException(
                    "retrieval cache evidence must belong to the key organization");
        }
    }

    static Key key(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            String queryHash,
            String strategy,
            String modelRouteFingerprint) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!scope.organizationId().equals(snapshot.namespace().organizationId())) {
            throw new IllegalArgumentException(
                    "authorization scope and projection snapshot belong to different organizations");
        }
        return new Key(
                snapshot,
                scope.authorizationFingerprint(),
                queryHash,
                strategy,
                modelRouteFingerprint);
    }

    record Key(
            ProjectionSnapshot snapshot,
            String authorizationFingerprint,
            String queryHash,
            String strategy,
            String modelRouteFingerprint) {

        public Key {
            Objects.requireNonNull(snapshot, "snapshot");
            authorizationFingerprint =
                    CanonicalCacheKeyHasher.requireSha256(
                            authorizationFingerprint, "authorizationFingerprint");
            queryHash =
                    CanonicalCacheKeyHasher.requireSha256(queryHash, "queryHash");
            strategy = requireText(strategy, "strategy");
            modelRouteFingerprint =
                    requireText(modelRouteFingerprint, "modelRouteFingerprint");
        }
    }

    record Entry(
            String mediaType,
            String payload,
            List<EvidenceReference> evidence,
            Instant createdAt,
            Instant expiresAt) {

        public Entry {
            mediaType = requireText(mediaType, "mediaType");
            payload = Objects.requireNonNull(payload, "payload");
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
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
