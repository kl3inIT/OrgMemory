package com.orgmemory.graphrag.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PermissionScopedGraphMergerTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PUBLIC_ASSET =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID RESTRICTED_ASSET =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final CanonicalEntity SOURCE = new CanonicalEntity(
            UUID.fromString("30000000-0000-0000-0000-000000000001"),
            "orgmemory");
    private static final CanonicalEntity TARGET = new CanonicalEntity(
            UUID.fromString("30000000-0000-0000-0000-000000000002"),
            "openfga");
    private static final CanonicalRelation RELATION = new CanonicalRelation(
            UUID.fromString("40000000-0000-0000-0000-000000000001"),
            SOURCE.id(),
            TARGET.id(),
            RelationOrientation.DIRECTED);

    @Test
    void mergesOnlyVisibleEvidenceAndSumsOnlyVisibleRelationSupport() {
        AuthorizedEvidenceScope publicScope = scope(Set.of(PUBLIC_ASSET));
        List<EntityContribution> entities = List.of(
                entityContribution(SOURCE, PUBLIC_ASSET, "Public source description"),
                entityContribution(TARGET, PUBLIC_ASSET, "Public target description"),
                entityContribution(
                        SOURCE,
                        RESTRICTED_ASSET,
                        "ACQUISITION_TARGET",
                        "Restricted acquisition plan"));
        List<RelationContribution> relations = List.of(
                relationContribution(PUBLIC_ASSET, "Public relation", 1.0),
                relationContribution(PUBLIC_ASSET, "Another public observation", 2.0),
                relationContribution(
                        RESTRICTED_ASSET,
                        "ACQUIRES",
                        "Restricted relation",
                        8.0));

        PermissionScopedGraphView view =
                PermissionScopedGraphMerger.merge(publicScope, entities, relations);

        assertEquals(2, view.entities().size());
        assertFalse(view.entities().stream()
                .flatMap(entity -> entity.descriptions().stream())
                .anyMatch(description -> description.contains("Restricted")));
        assertFalse(view.entities().stream()
                .flatMap(entity -> entity.types().stream())
                .anyMatch("ACQUISITION_TARGET"::equals));
        assertEquals(1, view.relations().size());
        assertEquals(3.0, view.relations().getFirst().weight());
        assertEquals(List.of("USES"), view.relations().getFirst().types());
        assertFalse(view.relations().getFirst().descriptions().stream()
                .anyMatch(description -> description.contains("Restricted")));
        assertEquals(
                publicScope.authorizationFingerprint(),
                view.relations().getFirst().summaryInput().authorizationFingerprint());
        assertEquals(
                "orgmemory -> openfga",
                view.relations().getFirst().summaryInput().subjectName());
    }

    @Test
    void projectionFingerprintChangesWhenVisibleEvidenceChanges() {
        AuthorizedEvidenceScope scope = scope(Set.of(PUBLIC_ASSET));
        List<EntityContribution> entities = List.of(
                entityContribution(SOURCE, PUBLIC_ASSET, "Source"),
                entityContribution(TARGET, PUBLIC_ASSET, "Target"));
        UUID contributionId =
                UUID.fromString("50000000-0000-0000-0000-000000000001");

        PermissionScopedGraphView one = PermissionScopedGraphMerger.merge(
                scope,
                entities,
                List.of(relationContribution(
                        contributionId, PUBLIC_ASSET, "USES", "First", 1.0)));
        PermissionScopedGraphView two = PermissionScopedGraphMerger.merge(
                scope,
                entities,
                List.of(relationContribution(
                        contributionId, PUBLIC_ASSET, "USES", "Second", 1.0)));

        assertNotEquals(one.projectionFingerprint(), two.projectionFingerprint());
    }

    private static AuthorizedEvidenceScope scope(Set<UUID> assetIds) {
        return new AuthorizedEvidenceScope(
                ORGANIZATION_ID,
                UUID.randomUUID(),
                null,
                false,
                assetIds,
                "model-1",
                1,
                Instant.parse("2026-07-24T00:00:00Z"));
    }

    private static EntityContribution entityContribution(
            CanonicalEntity entity,
            UUID assetId,
            String description) {
        return entityContribution(entity, assetId, "SYSTEM", description);
    }

    private static EntityContribution entityContribution(
            CanonicalEntity entity,
            UUID assetId,
            String type,
            String description) {
        UUID id = UUID.randomUUID();
        return new EntityContribution(
                id,
                entity,
                type,
                description,
                provenance(assetId, id));
    }

    private static RelationContribution relationContribution(
            UUID assetId,
            String description,
            double weight) {
        return relationContribution(assetId, "USES", description, weight);
    }

    private static RelationContribution relationContribution(
            UUID assetId,
            String type,
            String description,
            double weight) {
        return relationContribution(
                UUID.randomUUID(), assetId, type, description, weight);
    }

    private static RelationContribution relationContribution(
            UUID id,
            UUID assetId,
            String type,
            String description,
            double weight) {
        return new RelationContribution(
                id,
                RELATION,
                type,
                List.of("authorization"),
                description,
                weight,
                provenance(assetId, id));
    }

    private static EvidenceProvenance provenance(UUID assetId, UUID contributionId) {
        return new EvidenceProvenance(
                new EvidenceReference(
                        ORGANIZATION_ID,
                        assetId,
                        UUID.nameUUIDFromBytes(
                                (assetId + "revision").getBytes(StandardCharsets.UTF_8)),
                        contributionId,
                        UUID.nameUUIDFromBytes(
                                (assetId + "acl").getBytes(StandardCharsets.UTF_8)),
                        1),
                1,
                "openai",
                "gpt-5.6-sol",
                "prompt-v1",
                0.9,
                Instant.parse("2026-07-24T00:00:00Z"));
    }
}
