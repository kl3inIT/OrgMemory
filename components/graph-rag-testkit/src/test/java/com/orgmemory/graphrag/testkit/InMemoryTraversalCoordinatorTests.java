package com.orgmemory.graphrag.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryTraversalCoordinatorTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000001");
    private static final UUID ACTOR_ID =
            UUID.fromString("40000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void usesTheCoreCoordinatorForZeroEmptyAndUnauthorizedSeedResults() {
        InMemoryAuthorizedQueryProjection projection =
                new InMemoryAuthorizedQueryProjection();
        ProjectionSnapshot snapshot = new ProjectionSnapshot(
                UUID.randomUUID(),
                new ProjectionNamespace(ORGANIZATION_ID, "default", "knowledge"),
                1,
                "test-manifest",
                Set.of(ProjectionKind.GRAPH),
                NOW);
        AuthorizedEvidenceScope scope = new AuthorizedEvidenceScope(
                ORGANIZATION_ID,
                ACTOR_ID,
                null,
                false,
                Set.of(),
                "model-v1",
                1,
                NOW);

        assertEquals(
                List.of(),
                projection.expandEntityIds(
                        scope,
                        snapshot,
                        List.of(UUID.randomUUID()),
                        1,
                        0));
        assertEquals(
                List.of(),
                projection.expandEntityIds(
                        scope,
                        snapshot,
                        List.of(),
                        1,
                        1));
        assertEquals(
                List.of(),
                projection.expandEntityIds(
                        scope,
                        snapshot,
                        List.of(UUID.randomUUID()),
                        0,
                        1));
        assertEquals(3, projection.reads());
    }
}
