package com.orgmemory.graphrag.testkit;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Storage-neutral security and lifecycle contract for every {@link GraphStore}.
 */
public final class GraphStoreConformance {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private static final UUID ORGANIZATION_ID = id("organization");
    private static final UUID OTHER_ORGANIZATION_ID = id("other-organization");
    private static final UUID ACTOR_ID = id("actor");
    private static final UUID DEPARTMENT_ID = id("department");
    private static final UUID PUBLIC_ASSET_ID = id("public-asset");
    private static final UUID SECRET_ASSET_ID = id("secret-asset");
    private static final UUID PUBLIC_REVISION_ID = id("public-revision");
    private static final UUID SECRET_REVISION_ID = id("secret-revision");
    private static final UUID SHARED_ENTITY_ID = id("shared-entity");
    private static final UUID PUBLIC_NEIGHBOR_ID = id("public-neighbor");
    private static final UUID REPLACEMENT_NEIGHBOR_ID = id("replacement-neighbor");
    private static final UUID SECRET_NEIGHBOR_ID = id("secret-neighbor");
    private static final UUID PUBLIC_RELATION_ID = id("public-relation");
    private static final UUID REPLACEMENT_RELATION_ID = id("replacement-relation");
    private static final UUID SECRET_RELATION_ID = id("secret-relation");
    private static final ProjectionNamespace NAMESPACE =
            new ProjectionNamespace(ORGANIZATION_ID, "default", "knowledge");

    private GraphStoreConformance() {
    }

    public static UUID organizationId() {
        return ORGANIZATION_ID;
    }

    public static void verify(
            GraphStore store,
            ProjectionPublicationStore publications) {
        java.util.Objects.requireNonNull(store, "store");
        java.util.Objects.requireNonNull(publications, "publications");

        ProjectionBatch first = batch("first", 0, 1);
        store.stageReplaceRevision(first, publicRevision(1, false));
        store.stageReplaceRevision(first, secretRevision(1));
        ProjectionSnapshot firstSnapshot = publish(first, publications);

        AuthorizedEvidenceScope publicScope = scope(
                ORGANIZATION_ID, Set.of(PUBLIC_ASSET_ID));
        AuthorizedEvidenceScope allScope = scope(
                ORGANIZATION_ID, Set.of(PUBLIC_ASSET_ID, SECRET_ASSET_ID));
        AuthorizedEvidenceScope emptyScope = scope(ORGANIZATION_ID, Set.of());

        requireIds(
                store.loadEntities(
                        publicScope,
                        firstSnapshot,
                        List.of(
                                SHARED_ENTITY_ID,
                                PUBLIC_NEIGHBOR_ID,
                                SECRET_NEIGHBOR_ID)),
                Set.of(SHARED_ENTITY_ID, PUBLIC_NEIGHBOR_ID),
                "entity visibility must be contribution-scoped");
        requireIds(
                store.loadRelations(
                        publicScope,
                        firstSnapshot,
                        List.of(PUBLIC_RELATION_ID, SECRET_RELATION_ID)),
                Set.of(PUBLIC_RELATION_ID),
                "relation visibility must require visible evidence and endpoints");
        require(
                store.loadEntityContributions(
                                        publicScope,
                                        firstSnapshot,
                                        List.of(SHARED_ENTITY_ID))
                                .size()
                        == 1,
                "shared identity must expose only the authorized contribution");
        require(
                store.loadRelationContributions(
                                        publicScope,
                                        firstSnapshot,
                                        List.of(PUBLIC_RELATION_ID, SECRET_RELATION_ID))
                                .stream()
                                .map(RelationContribution::relation)
                                .map(CanonicalRelation::id)
                                .collect(java.util.stream.Collectors.toSet())
                                .equals(Set.of(PUBLIC_RELATION_ID)),
                "relation contributions must not cross the authorized asset set");

        Map<UUID, Long> publicDegrees = store.loadVisibleEntityDegrees(
                publicScope,
                firstSnapshot,
                List.of(SHARED_ENTITY_ID, PUBLIC_NEIGHBOR_ID, SECRET_NEIGHBOR_ID));
        require(publicDegrees.get(SHARED_ENTITY_ID) == 1L, "visible degree must be one");
        require(publicDegrees.get(PUBLIC_NEIGHBOR_ID) == 1L, "neighbor degree must be one");
        require(publicDegrees.get(SECRET_NEIGHBOR_ID) == 0L, "hidden degree must be zero");

        Map<UUID, Double> publicWeights = store.loadVisibleRelationWeights(
                publicScope,
                firstSnapshot,
                List.of(PUBLIC_RELATION_ID, SECRET_RELATION_ID));
        require(
                Double.compare(
                                publicWeights.getOrDefault(PUBLIC_RELATION_ID, 0.0),
                                1.5)
                        == 0,
                "visible relation weight must include authorized contributions");
        require(
                Double.compare(
                                publicWeights.getOrDefault(SECRET_RELATION_ID, 0.0),
                                0.0)
                        == 0,
                "hidden relation weight must not leak");
        require(
                store.expandEntityIds(
                                publicScope,
                                firstSnapshot,
                                List.of(SHARED_ENTITY_ID),
                                3,
                                10)
                        .equals(List.of(SHARED_ENTITY_ID, PUBLIC_NEIGHBOR_ID)),
                "bounded traversal must not cross into hidden evidence");

        require(
                store.loadEntities(
                                emptyScope,
                                firstSnapshot,
                                List.of(SHARED_ENTITY_ID))
                        .isEmpty(),
                "an empty authorization set must return no graph data");
        expect(
                IllegalArgumentException.class,
                () -> store.loadEntities(
                        scope(OTHER_ORGANIZATION_ID, Set.of(PUBLIC_ASSET_ID)),
                        firstSnapshot,
                        List.of(SHARED_ENTITY_ID)));
        ProjectionSnapshot fabricated = new ProjectionSnapshot(
                id("fabricated-batch"),
                NAMESPACE,
                1,
                firstSnapshot.manifestFingerprint(),
                Set.of(ProjectionKind.GRAPH),
                NOW);
        expect(
                ProjectionPublicationStore.PublicationConflictException.class,
                () -> store.loadEntities(
                        publicScope,
                        fabricated,
                        List.of(SHARED_ENTITY_ID)));

        ProjectionBatch second = batch("second", 1, 2);
        store.stageReplaceRevision(second, publicRevision(2, true));
        ProjectionSnapshot secondSnapshot = publish(second, publications);

        requireIds(
                store.loadEntities(
                        publicScope,
                        firstSnapshot,
                        List.of(PUBLIC_NEIGHBOR_ID, REPLACEMENT_NEIGHBOR_ID)),
                Set.of(PUBLIC_NEIGHBOR_ID),
                "published history must remain addressable");
        requireIds(
                store.loadEntities(
                        publicScope,
                        secondSnapshot,
                        List.of(
                                PUBLIC_NEIGHBOR_ID,
                                REPLACEMENT_NEIGHBOR_ID,
                                SECRET_NEIGHBOR_ID)),
                Set.of(REPLACEMENT_NEIGHBOR_ID),
                "replacement must remove obsolete public contributions");
        requireIds(
                store.loadEntities(
                        allScope,
                        secondSnapshot,
                        List.of(REPLACEMENT_NEIGHBOR_ID, SECRET_NEIGHBOR_ID)),
                Set.of(REPLACEMENT_NEIGHBOR_ID, SECRET_NEIGHBOR_ID),
                "copy-forward must preserve untouched revisions");

        ProjectionBatch loser = batch("loser", 2, 3);
        store.stageDeleteRevision(loser, SECRET_REVISION_ID);
        ProjectionSnapshot loserSnapshot = new ProjectionSnapshot(
                loser.id(),
                NAMESPACE,
                loser.generation(),
                loser.manifestFingerprint(),
                Set.of(ProjectionKind.GRAPH),
                NOW);
        expect(
                ProjectionPublicationStore.PublicationConflictException.class,
                () -> store.loadEntities(
                        allScope,
                        loserSnapshot,
                        List.of(SECRET_NEIGHBOR_ID)));
        store.discard(loser);

        ProjectionBatch third = batch("third", 2, 3);
        store.stageDeleteRevision(third, SECRET_REVISION_ID);
        ProjectionSnapshot thirdSnapshot = publish(third, publications);
        requireIds(
                store.loadEntities(
                        allScope,
                        thirdSnapshot,
                        List.of(
                                SHARED_ENTITY_ID,
                                REPLACEMENT_NEIGHBOR_ID,
                                SECRET_NEIGHBOR_ID)),
                Set.of(SHARED_ENTITY_ID, REPLACEMENT_NEIGHBOR_ID),
                "deleting one revision must preserve identities backed by other evidence");
        require(
                store.loadRelations(
                                allScope,
                                thirdSnapshot,
                                List.of(REPLACEMENT_RELATION_ID, SECRET_RELATION_ID))
                        .stream()
                        .map(CanonicalRelation::id)
                        .collect(java.util.stream.Collectors.toSet())
                        .equals(Set.of(REPLACEMENT_RELATION_ID)),
                "revision deletion must remove orphan topology");
    }

    private static ProjectionSnapshot publish(
            ProjectionBatch batch,
            ProjectionPublicationStore publications) {
        publications.markPrepared(batch, ProjectionKind.GRAPH, NOW);
        return publications.publish(batch, NOW);
    }

    private static GraphRevisionContributions publicRevision(
            long generation,
            boolean replacement) {
        CanonicalEntity shared = entity(SHARED_ENTITY_ID, "OrgMemory");
        CanonicalEntity neighbor = replacement
                ? entity(REPLACEMENT_NEIGHBOR_ID, "Secure retrieval")
                : entity(PUBLIC_NEIGHBOR_ID, "Enterprise memory");
        CanonicalRelation relation = new CanonicalRelation(
                replacement ? REPLACEMENT_RELATION_ID : PUBLIC_RELATION_ID,
                shared.id(),
                neighbor.id(),
                RelationOrientation.UNDIRECTED);
        Fixture fixture = new Fixture(
                PUBLIC_ASSET_ID,
                PUBLIC_REVISION_ID,
                id("public-acl"),
                id("public-chunk"));
        return new GraphRevisionContributions(
                ORGANIZATION_ID,
                PUBLIC_ASSET_ID,
                PUBLIC_REVISION_ID,
                generation,
                List.of(
                        entityContribution(
                                "public-shared-" + generation,
                                shared,
                                "PRODUCT",
                                "permission-aware organizational memory",
                                fixture,
                                generation),
                        entityContribution(
                                "public-neighbor-" + generation,
                                neighbor,
                                "CAPABILITY",
                                replacement
                                        ? "retrieves only authorized evidence"
                                        : "captures durable company knowledge",
                                fixture,
                                generation)),
                List.of(relationContribution(
                        "public-relation-" + generation,
                        relation,
                        "ENABLES",
                        replacement
                                ? List.of("secure", "retrieval")
                                : List.of("enterprise", "memory"),
                        fixture,
                        generation,
                        1.5)));
    }

    private static GraphRevisionContributions secretRevision(long generation) {
        CanonicalEntity shared = entity(SHARED_ENTITY_ID, "OrgMemory");
        CanonicalEntity secret = entity(SECRET_NEIGHBOR_ID, "Acquisition plan");
        CanonicalRelation relation = new CanonicalRelation(
                SECRET_RELATION_ID,
                shared.id(),
                secret.id(),
                RelationOrientation.DIRECTED);
        Fixture fixture = new Fixture(
                SECRET_ASSET_ID,
                SECRET_REVISION_ID,
                id("secret-acl"),
                id("secret-chunk"));
        return new GraphRevisionContributions(
                ORGANIZATION_ID,
                SECRET_ASSET_ID,
                SECRET_REVISION_ID,
                generation,
                List.of(
                        entityContribution(
                                "secret-shared-" + generation,
                                shared,
                                "PRODUCT",
                                "restricted acquisition context",
                                fixture,
                                generation),
                        entityContribution(
                                "secret-neighbor-" + generation,
                                secret,
                                "STRATEGY",
                                "restricted strategic evidence",
                                fixture,
                                generation)),
                List.of(relationContribution(
                        "secret-relation-" + generation,
                        relation,
                        "ACQUIRES",
                        List.of("restricted", "strategy"),
                        fixture,
                        generation,
                        9.0)));
    }

    private static EntityContribution entityContribution(
            String key,
            CanonicalEntity entity,
            String type,
            String description,
            Fixture fixture,
            long generation) {
        return new EntityContribution(
                id(key),
                entity,
                type,
                description,
                provenance(fixture, generation));
    }

    private static RelationContribution relationContribution(
            String key,
            CanonicalRelation relation,
            String type,
            List<String> keywords,
            Fixture fixture,
            long generation,
            double weight) {
        return new RelationContribution(
                id(key),
                relation,
                type,
                keywords,
                "relation " + type,
                weight,
                provenance(fixture, generation));
    }

    private static EvidenceProvenance provenance(Fixture fixture, long generation) {
        return new EvidenceProvenance(
                new EvidenceReference(
                        ORGANIZATION_ID,
                        fixture.assetId(),
                        fixture.revisionId(),
                        fixture.chunkId(),
                        fixture.aclSnapshotId(),
                        generation),
                generation,
                "test",
                "deterministic",
                "graph-conformance-v1",
                "0000000000000000000000000000000000000000000000000000000000000000",
                0.9,
                NOW);
    }

    private static ProjectionBatch batch(
            String key,
            long expectedPreviousGeneration,
            long generation) {
        return new ProjectionBatch(
                id("batch-" + key),
                NAMESPACE,
                expectedPreviousGeneration,
                generation,
                "graph-conformance-" + key,
                "manifest-" + key,
                Set.of(ProjectionKind.GRAPH),
                NOW);
    }

    private static AuthorizedEvidenceScope scope(
            UUID organizationId,
            Set<UUID> assets) {
        return new AuthorizedEvidenceScope(
                organizationId,
                ACTOR_ID,
                DEPARTMENT_ID,
                false,
                assets,
                "model-v1",
                1,
                NOW);
    }

    private static CanonicalEntity entity(UUID id, String name) {
        return new CanonicalEntity(id, name);
    }

    private static void requireIds(
            Collection<? extends Object> records,
            Set<UUID> expected,
            String message) {
        Set<UUID> actual = records.stream()
                .map(record -> {
                    if (record instanceof CanonicalEntity entity) {
                        return entity.id();
                    }
                    if (record instanceof CanonicalRelation relation) {
                        return relation.id();
                    }
                    throw new AssertionError("unsupported graph record " + record);
                })
                .collect(java.util.stream.Collectors.toSet());
        require(actual.equals(expected), message + ": expected " + expected + " but got " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expect(
            Class<? extends Throwable> type,
            Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            if (type.isInstance(throwable)) {
                return;
            }
            throw new AssertionError(
                    "expected " + type.getSimpleName() + " but got " + throwable,
                    throwable);
        }
        throw new AssertionError("expected " + type.getSimpleName());
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private record Fixture(
            UUID assetId,
            UUID revisionId,
            UUID aclSnapshotId,
            UUID chunkId) {
    }
}
