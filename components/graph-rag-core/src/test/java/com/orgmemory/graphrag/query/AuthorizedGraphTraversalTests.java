package com.orgmemory.graphrag.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.storage.AuthorizedGraphTraversalSource;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore.PublicationConflictException;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizedGraphTraversalTests {

    private static final UUID ORGANIZATION_ID = id("organization");
    private static final UUID ACTOR_ID = id("actor");
    private static final Instant NOW = Instant.parse("2026-08-01T00:00:00Z");
    private static final ProjectionSnapshot SNAPSHOT = new ProjectionSnapshot(
            id("batch"),
            new ProjectionNamespace(ORGANIZATION_ID, "default", "knowledge"),
            1,
            "manifest-v1",
            Set.of(ProjectionKind.GRAPH),
            NOW);
    private static final AuthorizedEvidenceScope SCOPE = new AuthorizedEvidenceScope(
            ORGANIZATION_ID,
            ACTOR_ID,
            null,
            false,
            Set.of(id("asset")),
            "model-v1",
            1,
            NOW);

    @Test
    void validatesTheExactSnapshotBeforeEveryEarlyReturn() {
        FakeSource source = new FakeSource(SNAPSHOT, List.of(), List.of());
        AuthorizedGraphTraversal traversal = new AuthorizedGraphTraversal(source, 1);
        ProjectionSnapshot fabricated = new ProjectionSnapshot(
                id("fabricated"),
                SNAPSHOT.namespace(),
                SNAPSHOT.generation(),
                SNAPSHOT.manifestFingerprint(),
                SNAPSHOT.projections(),
                SNAPSHOT.publishedAt());

        assertThrows(
                PublicationConflictException.class,
                () -> traversal.expandEntityIds(SCOPE, fabricated, List.of(), 1, 1));
        assertThrows(
                PublicationConflictException.class,
                () -> traversal.expandEntityIds(
                        SCOPE,
                        fabricated,
                        List.of(id("seed")),
                        1,
                        0));
        assertEquals(List.of(), traversal.expandEntityIds(SCOPE, SNAPSHOT, List.of(), 1, 1));
        assertEquals(
                List.of(),
                traversal.expandEntityIds(
                        SCOPE,
                        SNAPSHOT,
                        List.of(id("seed")),
                        1,
                        0));
        assertEquals(4, source.validations);
    }

    @Test
    void authorizesAndSortsDepthZeroSeedsBeforeApplyingTheGlobalLimit() {
        UUID high = UUID.fromString("ffffffff-0000-0000-0000-000000000001");
        UUID low = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID hidden = id("hidden");
        FakeSource source = new FakeSource(
                SNAPSHOT,
                List.of(entity(high), entity(low)),
                List.of());

        assertEquals(
                List.of(low),
                new AuthorizedGraphTraversal(source, 1).expandEntityIds(
                        SCOPE,
                        SNAPSHOT,
                        List.of(high, hidden, low, high),
                        0,
                        1));
    }

    @Test
    void drainsCompletePagesAndOrdersEachMinimumDepthByCanonicalUuid() {
        UUID seedHigh = UUID.fromString("ffffffff-0000-0000-0000-000000000010");
        UUID seedLow = UUID.fromString("00000000-0000-0000-0000-000000000010");
        UUID depthOneHigh = UUID.fromString("ffffffff-0000-0000-0000-000000000020");
        UUID depthOneLow = UUID.fromString("00000000-0000-0000-0000-000000000020");
        UUID depthTwo = id("depth-two");
        FakeSource source = new FakeSource(
                SNAPSHOT,
                List.of(
                        entity(seedHigh),
                        entity(seedLow),
                        entity(depthOneHigh),
                        entity(depthOneLow),
                        entity(depthTwo)),
                List.of(
                        relation("relation-z", seedHigh, depthOneHigh),
                        relation("relation-a", seedLow, depthOneLow),
                        relation("relation-depth-two", depthOneLow, depthTwo)));

        assertEquals(
                List.of(seedLow, seedHigh, depthOneLow, depthOneHigh, depthTwo),
                new AuthorizedGraphTraversal(source, 1).expandEntityIds(
                        SCOPE,
                        SNAPSHOT,
                        List.of(seedHigh, seedLow),
                        2,
                        10));
        assertEquals(5, source.pageReads);
    }

    @Test
    void rejectsStructuralInputsBeforeTouchingTheSource() {
        FakeSource source = new FakeSource(SNAPSHOT, List.of(), List.of());
        AuthorizedGraphTraversal traversal = new AuthorizedGraphTraversal(source, 1);

        assertThrows(
                IllegalArgumentException.class,
                () -> traversal.expandEntityIds(SCOPE, SNAPSHOT, List.of(), -1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> traversal.expandEntityIds(SCOPE, SNAPSHOT, List.of(), 1, -1));
        assertThrows(
                NullPointerException.class,
                () -> traversal.expandEntityIds(
                        SCOPE,
                        SNAPSHOT,
                        java.util.Arrays.asList(id("seed"), null),
                        1,
                        1));
        assertEquals(0, source.validations);
    }

    @Test
    void rejectsMalformedRelationPagesAndCursors() {
        CanonicalRelation first = relation("relation-a", id("seed"), id("neighbor-a"));
        CanonicalRelation second = relation("relation-b", id("seed"), id("neighbor-b"));
        List<CanonicalRelation> descending = List.of(first, second).stream()
                .sorted(java.util.Comparator.comparing(
                                CanonicalRelation::id,
                                AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER)
                        .reversed())
                .toList();

        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizedGraphTraversalSource.IncidentRelationPage(
                        descending,
                        null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AuthorizedGraphTraversalSource.IncidentRelationPage(
                        List.of(first),
                        second.id()));
    }

    @Test
    void rejectsARepeatedCursorPageAndRelationsOutsideTheFrontier() {
        UUID seed = id("seed");
        CanonicalRelation incident = relation("incident", seed, id("neighbor"));
        FakeSource repeatedPageSource = new FakeSource(
                SNAPSHOT,
                List.of(entity(seed)),
                List.of()) {
            private int reads;

            @Override
            public IncidentRelationPage loadIncidentRelationPage(
                    AuthorizedEvidenceScope scope,
                    ProjectionSnapshot snapshot,
                    Collection<UUID> entityIds,
                    UUID afterRelationId,
                    int pageSize) {
                reads++;
                return new IncidentRelationPage(
                        List.of(incident),
                        reads == 1 ? incident.id() : null);
            }
        };
        FakeSource outsideFrontierSource = new FakeSource(
                SNAPSHOT,
                List.of(entity(seed)),
                List.of()) {
            @Override
            public IncidentRelationPage loadIncidentRelationPage(
                    AuthorizedEvidenceScope scope,
                    ProjectionSnapshot snapshot,
                    Collection<UUID> entityIds,
                    UUID afterRelationId,
                    int pageSize) {
                return new IncidentRelationPage(
                        List.of(relation("outside", id("other-a"), id("other-b"))),
                        null);
            }
        };

        assertThrows(
                IllegalStateException.class,
                () -> new AuthorizedGraphTraversal(repeatedPageSource, 1)
                        .expandEntityIds(SCOPE, SNAPSHOT, List.of(seed), 1, 10));
        assertThrows(
                IllegalStateException.class,
                () -> new AuthorizedGraphTraversal(outsideFrontierSource, 1)
                        .expandEntityIds(SCOPE, SNAPSHOT, List.of(seed), 1, 10));
    }

    @Test
    void rejectsAPageLargerThanTheRequestedBound() {
        UUID seed = id("seed");
        List<CanonicalRelation> oversized = List.of(
                        relation("oversized-a", seed, id("neighbor-a")),
                        relation("oversized-b", seed, id("neighbor-b")))
                .stream()
                .sorted(java.util.Comparator.comparing(
                        CanonicalRelation::id,
                        AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER))
                .toList();
        FakeSource source = new FakeSource(
                SNAPSHOT,
                List.of(entity(seed)),
                List.of()) {
            @Override
            public IncidentRelationPage loadIncidentRelationPage(
                    AuthorizedEvidenceScope scope,
                    ProjectionSnapshot snapshot,
                    Collection<UUID> entityIds,
                    UUID afterRelationId,
                    int pageSize) {
                return new IncidentRelationPage(oversized, null);
            }
        };

        assertThrows(
                IllegalStateException.class,
                () -> new AuthorizedGraphTraversal(source, 1)
                        .expandEntityIds(SCOPE, SNAPSHOT, List.of(seed), 1, 10));
    }

    @Test
    void rejectsEntitiesTheSourceDidNotReceiveAsRequested() {
        UUID seed = id("seed");
        FakeSource source = new FakeSource(SNAPSHOT, List.of(), List.of()) {
            @Override
            public List<CanonicalEntity> loadEntities(
                    AuthorizedEvidenceScope scope,
                    ProjectionSnapshot snapshot,
                    Collection<UUID> entityIds) {
                return List.of(entity(id("unrequested")));
            }
        };

        assertThrows(
                IllegalStateException.class,
                () -> new AuthorizedGraphTraversal(source, 1)
                        .expandEntityIds(SCOPE, SNAPSHOT, List.of(seed), 1, 10));
    }

    private static CanonicalEntity entity(UUID id) {
        return new CanonicalEntity(id, id.toString());
    }

    private static CanonicalRelation relation(
            String key,
            UUID source,
            UUID target) {
        return new CanonicalRelation(
                id(key),
                source,
                target,
                RelationOrientation.UNDIRECTED);
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static class FakeSource implements AuthorizedGraphTraversalSource {

        private final ProjectionSnapshot expectedSnapshot;
        private final Map<UUID, CanonicalEntity> entities;
        private final List<CanonicalRelation> relations;
        private int validations;
        private int pageReads;

        private FakeSource(
                ProjectionSnapshot expectedSnapshot,
                Collection<CanonicalEntity> entities,
                Collection<CanonicalRelation> relations) {
            this.expectedSnapshot = expectedSnapshot;
            this.entities = entities.stream().collect(java.util.stream.Collectors.toMap(
                    CanonicalEntity::id,
                    entity -> entity,
                    (left, right) -> left,
                    LinkedHashMap::new));
            this.relations = relations.stream()
                    .sorted(java.util.Comparator.comparing(
                            CanonicalRelation::id,
                            AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER))
                    .toList();
        }

        @Override
        public void validateSnapshot(
                AuthorizedEvidenceScope scope,
                ProjectionSnapshot snapshot) {
            validations++;
            if (!expectedSnapshot.equals(snapshot)) {
                throw new PublicationConflictException("fabricated snapshot");
            }
        }

        @Override
        public List<CanonicalEntity> loadEntities(
                AuthorizedEvidenceScope scope,
                ProjectionSnapshot snapshot,
                Collection<UUID> entityIds) {
            return entityIds.stream()
                    .distinct()
                    .map(entities::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        @Override
        public IncidentRelationPage loadIncidentRelationPage(
                AuthorizedEvidenceScope scope,
                ProjectionSnapshot snapshot,
                Collection<UUID> entityIds,
                UUID afterRelationId,
                int pageSize) {
            pageReads++;
            Set<UUID> frontier = Set.copyOf(entityIds);
            List<CanonicalRelation> fetched = relations.stream()
                    .filter(relation -> frontier.stream().anyMatch(relation::isIncidentTo))
                    .filter(relation -> afterRelationId == null
                            || AuthorizedGraphTraversalSource.CANONICAL_UUID_ORDER.compare(
                                    relation.id(), afterRelationId) > 0)
                    .limit((long) pageSize + 1)
                    .toList();
            boolean hasMore = fetched.size() > pageSize;
            List<CanonicalRelation> page = hasMore
                    ? List.copyOf(fetched.subList(0, pageSize))
                    : fetched;
            return new IncidentRelationPage(
                    page,
                    hasMore ? page.getLast().id() : null);
        }
    }
}
