package com.orgmemory.graphrag.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CacheKeyContractTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID ASSET_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final ProjectionNamespace NAMESPACE =
            new ProjectionNamespace(ORGANIZATION_ID, "default", "knowledge");
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void canonicalHasherIsOrderIndependentAndBoundarySafe() {
        var first = new LinkedHashMap<String, String>();
        first.put("left", "ab");
        first.put("right", "c");
        var reordered = new LinkedHashMap<String, String>();
        reordered.put("right", "c");
        reordered.put("left", "ab");

        assertEquals(
                CanonicalCacheKeyHasher.sha256("test", first),
                CanonicalCacheKeyHasher.sha256("test", reordered));
        assertNotEquals(
                CanonicalCacheKeyHasher.sha256("test", first),
                CanonicalCacheKeyHasher.sha256(
                        "test", java.util.Map.of("left", "a", "right", "bc")));
    }

    @Test
    void keywordKeyChangesForEveryOutputAffectingDimension() {
        ModelInvocationCache.Key baseline = ModelInvocationCacheKeys.keywords(
                NAMESPACE, "leave policy", "en", "MIX", "route-v1", "profile-v1");

        assertNotEquals(
                baseline,
                ModelInvocationCacheKeys.keywords(
                        NAMESPACE,
                        "leave policy",
                        "vi",
                        "MIX",
                        "route-v1",
                        "profile-v1"));
        assertNotEquals(
                baseline,
                ModelInvocationCacheKeys.keywords(
                        NAMESPACE,
                        "leave policy",
                        "en",
                        "LOCAL",
                        "route-v1",
                        "profile-v1"));
    }

    @Test
    void summaryKeyCannotCrossAuthorizationScopes() {
        ModelInvocationCache.Key first =
                ModelInvocationCacheKeys.permissionScopedSummary(
                        NAMESPACE,
                        "visible descriptions",
                        "authorization-a",
                        "route-v1",
                        "profile-v1");
        ModelInvocationCache.Key second =
                ModelInvocationCacheKeys.permissionScopedSummary(
                        NAMESPACE,
                        "visible descriptions",
                        "authorization-b",
                        "route-v1",
                        "profile-v1");

        assertNotEquals(first, second);
    }

    @Test
    void queryKeyChangesWithSemanticsAuthorizationAndPublication() {
        AuthorizedEvidenceScope scope = scope(7);
        ProjectionSnapshot generationOne = snapshot(1);
        ProjectionSnapshot generationTwo = snapshot(2);
        var semantics = semantics(false);

        RetrievalResultCache.Key baseline = RetrievalResultCacheKeys.query(
                scope, generationOne, semantics, "route-v1");

        assertNotEquals(
                baseline,
                RetrievalResultCacheKeys.query(
                        scope(8), generationOne, semantics, "route-v1"));
        assertNotEquals(
                baseline,
                RetrievalResultCacheKeys.query(
                        scope, generationTwo, semantics, "route-v1"));
        assertNotEquals(
                baseline,
                RetrievalResultCacheKeys.query(
                        scope, generationOne, semantics(true), "route-v1"));
    }

    @Test
    void exactCacheHashesRejectNonCanonicalDigests() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelInvocationCache.Key(
                        NAMESPACE,
                        "EXTRACTION",
                        "not-a-sha256-digest",
                        "route-v1",
                        "profile-v1"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RetrievalResultCache.Key(
                        snapshot(1),
                        "A".repeat(64),
                        "b".repeat(64),
                        "MIX",
                        "route-v1"));
    }

    @Test
    void retrievalCacheRejectsEvidenceFromAnotherOrganization() {
        RetrievalResultCache.Key key = RetrievalResultCacheKeys.query(
                scope(7), snapshot(1), semantics(false), "route-v1");
        RetrievalResultCache.Entry entry = new RetrievalResultCache.Entry(
                "application/json",
                "{}",
                List.of(new EvidenceReference(
                        UUID.fromString("20000000-0000-0000-0000-000000000001"),
                        ASSET_ID,
                        UUID.fromString("10000000-0000-0000-0000-000000000004"),
                        UUID.fromString("10000000-0000-0000-0000-000000000005"),
                        UUID.fromString("10000000-0000-0000-0000-000000000006"),
                        1)),
                NOW,
                NOW.plusSeconds(30));

        assertThrows(
                IllegalArgumentException.class,
                () -> RetrievalResultCache.requireValidEntry(key, entry));
    }

    private static RetrievalResultCacheKeys.QuerySemantics semantics(
            boolean includeHeadings) {
        return new RetrievalResultCacheKeys.QuerySemantics(
                "what is the leave policy",
                "MIX",
                "compact",
                20,
                10,
                1_000,
                1_000,
                2_000,
                "leave,policy",
                "days",
                "",
                "reranker-v1",
                includeHeadings,
                false);
    }

    private static AuthorizedEvidenceScope scope(long aclGeneration) {
        return new AuthorizedEvidenceScope(
                ORGANIZATION_ID,
                ACTOR_ID,
                null,
                false,
                Set.of(ASSET_ID),
                "model-v1",
                aclGeneration,
                NOW);
    }

    private static ProjectionSnapshot snapshot(long generation) {
        return new ProjectionSnapshot(
                UUID.nameUUIDFromBytes(("snapshot-" + generation)
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                NAMESPACE,
                generation,
                "manifest-" + generation,
                Set.of(ProjectionKind.CONTENT),
                NOW.plusSeconds(generation));
    }
}
