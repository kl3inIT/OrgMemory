package com.orgmemory.graphrag.testkit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryModelInvocationCacheTests {

    private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");
    private static final ProjectionNamespace NAMESPACE = new ProjectionNamespace(
            UUID.fromString("10000000-0000-0000-0000-000000000001"),
            "main",
            "knowledge");

    @Test
    void rejectsInvalidBoundedWritesBeforeMutatingEntries() {
        InMemoryModelInvocationCache cache = new InMemoryModelInvocationCache();
        ModelInvocationCache.Key seed = key(NAMESPACE, "QUERY_EMBEDDING", 1);
        cache.put(seed, entry(NOW));
        ProjectionNamespace otherNamespace = new ProjectionNamespace(
                UUID.fromString("10000000-0000-0000-0000-000000000002"),
                "main",
                "knowledge");
        ModelInvocationCache.Key mismatchedNamespace =
                key(otherNamespace, "QUERY_EMBEDDING", 2);
        ModelInvocationCache.Key mismatchedOperation =
                key(NAMESPACE, "KEYWORD_EXTRACTION", 3);

        assertThrows(
                IllegalArgumentException.class,
                () -> cache.putBounded(
                        NAMESPACE,
                        "QUERY_EMBEDDING",
                        Map.of(key(NAMESPACE, "QUERY_EMBEDDING", 4), entry(NOW)),
                        NOW,
                        0));
        assertThrows(
                IllegalArgumentException.class,
                () -> cache.putBounded(
                        NAMESPACE,
                        "QUERY_EMBEDDING",
                        Map.of(mismatchedNamespace, entry(NOW)),
                        NOW,
                        1));
        assertThrows(
                IllegalArgumentException.class,
                () -> cache.putBounded(
                        NAMESPACE,
                        "QUERY_EMBEDDING",
                        Map.of(mismatchedOperation, entry(NOW)),
                        NOW,
                        1));

        assertTrue(cache.get(seed, NOW).isPresent());
        assertFalse(cache.get(mismatchedNamespace, NOW).isPresent());
        assertFalse(cache.get(mismatchedOperation, NOW).isPresent());
    }

    @Test
    void normalizesOperationBeforeApplyingTheBound() {
        InMemoryModelInvocationCache cache = new InMemoryModelInvocationCache();
        ModelInvocationCache.Key older = key(NAMESPACE, "QUERY_EMBEDDING", 1);
        ModelInvocationCache.Key newer = key(NAMESPACE, "QUERY_EMBEDDING", 2);
        Map<ModelInvocationCache.Key, ModelInvocationCache.Entry> entries = new LinkedHashMap<>();
        entries.put(older, entry(NOW.minusSeconds(1)));
        entries.put(newer, entry(NOW));

        cache.putBounded(NAMESPACE, "  QUERY_EMBEDDING  ", entries, NOW, 1);

        assertFalse(cache.get(older, NOW).isPresent());
        assertTrue(cache.get(newer, NOW).isPresent());
    }

    private static ModelInvocationCache.Key key(
            ProjectionNamespace namespace,
            String operation,
            int suffix) {
        return new ModelInvocationCache.Key(
                namespace,
                operation,
                String.format("%064x", suffix),
                "test-route",
                "test-profile");
    }

    private static ModelInvocationCache.Entry entry(Instant createdAt) {
        return new ModelInvocationCache.Entry(
                "application/test",
                "payload",
                createdAt,
                createdAt.plus(Duration.ofDays(1)));
    }
}
