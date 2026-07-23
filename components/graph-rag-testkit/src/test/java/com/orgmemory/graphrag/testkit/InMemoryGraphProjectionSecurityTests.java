package com.orgmemory.graphrag.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.authorization.AuthorizedGraphScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InMemoryGraphProjectionSecurityTests {

    private static final UUID ORGANIZATION_ID = id("organization");
    private static final UUID ACTOR_ID = id("actor");
    private static final UUID ALLOWED_ASSET_ID = id("allowed-asset");
    private static final UUID RESTRICTED_ASSET_ID = id("restricted-asset");
    private static final UUID ALLOWED_REVISION_ID = id("allowed-revision");
    private static final UUID RESTRICTED_REVISION_ID = id("restricted-revision");
    private static final UUID SHARED_ENTITY_ID = id("shared-entity");
    private static final UUID PUBLIC_NEIGHBOR_ID = id("public-neighbor");
    private static final UUID SECRET_NEIGHBOR_ID = id("secret-neighbor");
    private static final UUID PUBLIC_RELATION_ID = id("public-relation");
    private static final UUID SECRET_RELATION_ID = id("secret-relation");

    private final CanonicalEntity sharedEntity =
            new CanonicalEntity(SHARED_ENTITY_ID, "OrgMemory", "PRODUCT");
    private final CanonicalEntity publicNeighbor =
            new CanonicalEntity(PUBLIC_NEIGHBOR_ID, "Secure Search", "CAPABILITY");
    private final CanonicalEntity secretNeighbor =
            new CanonicalEntity(SECRET_NEIGHBOR_ID, "Project Nightfall", "INITIATIVE");
    private final CanonicalRelation publicRelation = new CanonicalRelation(
            PUBLIC_RELATION_ID,
            SHARED_ENTITY_ID,
            PUBLIC_NEIGHBOR_ID,
            "BUILDS",
            RelationOrientation.DIRECTED);
    private final CanonicalRelation secretRelation = new CanonicalRelation(
            SECRET_RELATION_ID,
            SHARED_ENTITY_ID,
            SECRET_NEIGHBOR_ID,
            "ACQUIRES",
            RelationOrientation.DIRECTED);

    private InMemoryGraphProjection projection;

    @BeforeEach
    void setUp() {
        projection = new InMemoryGraphProjection();
        projection.replaceRevision(new GraphRevisionContributions(
                ORGANIZATION_ID,
                ALLOWED_ASSET_ID,
                ALLOWED_REVISION_ID,
                1,
                List.of(
                        entityContribution(
                                "allowed-shared",
                                sharedEntity,
                                "OrgMemory provides approved secure retrieval.",
                                ALLOWED_ASSET_ID,
                                ALLOWED_REVISION_ID,
                                0.9),
                        entityContribution(
                                "allowed-neighbor",
                                publicNeighbor,
                                "Secure Search filters evidence before ranking.",
                                ALLOWED_ASSET_ID,
                                ALLOWED_REVISION_ID,
                                0.8)),
                List.of(relationContribution(
                        "allowed-relation",
                        publicRelation,
                        List.of("security", "retrieval"),
                        "OrgMemory builds Secure Search.",
                        ALLOWED_ASSET_ID,
                        ALLOWED_REVISION_ID,
                        0.6))));
        projection.replaceRevision(new GraphRevisionContributions(
                ORGANIZATION_ID,
                RESTRICTED_ASSET_ID,
                RESTRICTED_REVISION_ID,
                1,
                List.of(
                        entityContribution(
                                "restricted-shared",
                                sharedEntity,
                                "OrgMemory is preparing the confidential Nightfall acquisition.",
                                RESTRICTED_ASSET_ID,
                                RESTRICTED_REVISION_ID,
                                1.0),
                        entityContribution(
                                "restricted-neighbor",
                                secretNeighbor,
                                "Project Nightfall is a confidential acquisition target.",
                                RESTRICTED_ASSET_ID,
                                RESTRICTED_REVISION_ID,
                                1.0)),
                List.of(relationContribution(
                        "restricted-relation",
                        secretRelation,
                        List.of("nightfall", "acquisition"),
                        "OrgMemory acquires Project Nightfall.",
                        RESTRICTED_ASSET_ID,
                        RESTRICTED_REVISION_ID,
                        1.0))));
    }

    @Test
    void restrictedContributionCannotAffectDescriptionsSeedsOrScores() {
        AuthorizedGraphScope scope = scope(Set.of(ALLOWED_ASSET_ID));

        List<EntityContribution> visible =
                projection.loadEntityContributions(scope, List.of(SHARED_ENTITY_ID));

        assertEquals(1, visible.size());
        assertEquals("OrgMemory provides approved secure retrieval.", visible.getFirst().description());
        assertTrue(projection.searchEntities(scope, "nightfall", 10).isEmpty());
        assertTrue(projection.searchRelations(scope, "acquisition", 10).isEmpty());
    }

    @Test
    void restrictedNeighborDegreeAndWeightDoNotLeak() {
        AuthorizedGraphScope allowedOnly = scope(Set.of(ALLOWED_ASSET_ID));
        AuthorizedGraphScope allowAll = scope(Set.of(ALLOWED_ASSET_ID, RESTRICTED_ASSET_ID));

        assertEquals(
                List.of(PUBLIC_RELATION_ID),
                projection.loadIncidentRelations(allowedOnly, List.of(SHARED_ENTITY_ID), 10)
                        .stream()
                        .map(CanonicalRelation::id)
                        .toList());
        assertEquals(1L, projection.loadVisibleEntityDegrees(
                allowedOnly, List.of(SHARED_ENTITY_ID)).get(SHARED_ENTITY_ID));
        assertEquals(2L, projection.loadVisibleEntityDegrees(
                allowAll, List.of(SHARED_ENTITY_ID)).get(SHARED_ENTITY_ID));
        assertEquals(
                0.0,
                projection.loadVisibleRelationWeights(
                        allowedOnly, List.of(SECRET_RELATION_ID)).get(SECRET_RELATION_ID));
        assertEquals(
                1.0,
                projection.loadVisibleRelationWeights(
                        allowAll, List.of(SECRET_RELATION_ID)).get(SECRET_RELATION_ID));
    }

    @Test
    void replacingOrRemovingOneRevisionPreservesOtherEvidence() {
        projection.replaceRevision(new GraphRevisionContributions(
                ORGANIZATION_ID,
                ALLOWED_ASSET_ID,
                ALLOWED_REVISION_ID,
                1,
                List.of(entityContribution(
                        "replacement-shared",
                        sharedEntity,
                        "Updated approved description.",
                        ALLOWED_ASSET_ID,
                        ALLOWED_REVISION_ID,
                        0.95)),
                List.of()));

        assertEquals(
                "Updated approved description.",
                projection.loadEntityContributions(
                                scope(Set.of(ALLOWED_ASSET_ID)),
                                List.of(SHARED_ENTITY_ID))
                        .getFirst()
                        .description());
        assertEquals(
                1,
                projection.loadEntityContributions(
                                scope(Set.of(RESTRICTED_ASSET_ID)),
                                List.of(SHARED_ENTITY_ID))
                        .size());

        projection.removeRevision(ORGANIZATION_ID, ALLOWED_REVISION_ID);

        assertTrue(projection.loadEntityContributions(
                        scope(Set.of(ALLOWED_ASSET_ID)),
                        List.of(SHARED_ENTITY_ID))
                .isEmpty());
        assertEquals(
                1,
                projection.loadEntityContributions(
                                scope(Set.of(RESTRICTED_ASSET_ID)),
                                List.of(SHARED_ENTITY_ID))
                        .size());
    }

    @Test
    void rejectedReplacementLeavesTheExistingRevisionIntact() {
        EntityContribution collidingContribution = new EntityContribution(
                id("restricted-shared"),
                sharedEntity,
                "This id belongs to the restricted revision.",
                provenance(
                        "replacement-collision",
                        ALLOWED_ASSET_ID,
                        ALLOWED_REVISION_ID,
                        0.5));

        assertThrows(
                IllegalArgumentException.class,
                () -> projection.replaceRevision(new GraphRevisionContributions(
                        ORGANIZATION_ID,
                        ALLOWED_ASSET_ID,
                        ALLOWED_REVISION_ID,
                        1,
                        List.of(collidingContribution),
                        List.of())));
        assertEquals(
                "OrgMemory provides approved secure retrieval.",
                projection.loadEntityContributions(
                                scope(Set.of(ALLOWED_ASSET_ID)),
                                List.of(SHARED_ENTITY_ID))
                        .getFirst()
                        .description());
    }

    private static AuthorizedGraphScope scope(Set<UUID> assets) {
        return new AuthorizedGraphScope(
                ORGANIZATION_ID,
                ACTOR_ID,
                null,
                false,
                assets,
                "model-v1",
                Instant.parse("2026-07-23T00:00:00Z"));
    }

    private static EntityContribution entityContribution(
            String key,
            CanonicalEntity entity,
            String description,
            UUID assetId,
            UUID revisionId,
            double confidence) {
        return new EntityContribution(
                id(key),
                entity,
                description,
                provenance(key, assetId, revisionId, confidence));
    }

    private static RelationContribution relationContribution(
            String key,
            CanonicalRelation relation,
            List<String> keywords,
            String description,
            UUID assetId,
            UUID revisionId,
            double confidence) {
        return new RelationContribution(
                id(key),
                relation,
                keywords,
                description,
                provenance(key, assetId, revisionId, confidence));
    }

    private static EvidenceProvenance provenance(
            String key,
            UUID assetId,
            UUID revisionId,
            double confidence) {
        return new EvidenceProvenance(
                ORGANIZATION_ID,
                assetId,
                revisionId,
                id(key + "-chunk"),
                id(key + "-acl"),
                1,
                1,
                "fixture",
                "deterministic",
                "lightrag-v1.5.4-orgmemory-v1",
                confidence,
                Instant.parse("2026-07-23T00:00:00Z"));
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
