package com.orgmemory.graphrag.curation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EffectiveGraphCurationTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final ProjectionNamespace NAMESPACE =
            new ProjectionNamespace(ORGANIZATION_ID, "default", "knowledge");
    private static final CurationProvenance PROVENANCE = new CurationProvenance(
            UUID.fromString("20000000-0000-0000-0000-000000000002"),
            "model-v1",
            7,
            Instant.parse("2026-07-24T00:00:00Z"),
            "duplicate identity");

    @Test
    void aliasesAreTransitiveAndSuppressionAppliesAfterCanonicalization() {
        GraphIdentityRef first = GraphIdentityRef.entity(uuid("first"));
        GraphIdentityRef second = GraphIdentityRef.entity(uuid("second"));
        GraphIdentityRef terminal = GraphIdentityRef.entity(uuid("terminal"));
        EffectiveGraphCuration curation = EffectiveGraphCuration.from(List.of(
                alias("a", first, second),
                alias("b", second, terminal),
                suppression("s", terminal)));

        assertTrue(curation.effective(first).isEmpty());
        assertTrue(curation.effective(second).isEmpty());
        assertTrue(curation.effective(terminal).isEmpty());
    }

    @Test
    void conflictingAndCyclicAliasesFailClosed() {
        GraphIdentityRef first = GraphIdentityRef.entity(uuid("first"));
        GraphIdentityRef second = GraphIdentityRef.entity(uuid("second"));
        GraphIdentityRef third = GraphIdentityRef.entity(uuid("third"));

        assertThrows(
                EffectiveGraphCuration.InvalidCurationOverlayException.class,
                () -> EffectiveGraphCuration.from(List.of(
                        alias("a", first, second),
                        alias("b", first, third))));
        assertThrows(
                EffectiveGraphCuration.InvalidCurationOverlayException.class,
                () -> EffectiveGraphCuration.from(List.of(
                        alias("a", first, second),
                        alias("b", second, first))));
    }

    @Test
    void relationThatBecomesSelfLoopIsExcluded() {
        UUID first = uuid("first");
        UUID second = uuid("second");
        CanonicalRelation relation = new CanonicalRelation(
                uuid("relation"), first, second, RelationOrientation.DIRECTED);
        EffectiveGraphCuration curation = EffectiveGraphCuration.from(List.of(
                alias(
                        "a",
                        GraphIdentityRef.entity(first),
                        GraphIdentityRef.entity(second))));

        assertTrue(curation.effective(relation).isEmpty());
    }

    @Test
    void relationAliasAndEndpointAliasesProduceEffectiveViewWithoutMutation() {
        UUID first = uuid("first");
        UUID second = uuid("second");
        UUID canonicalFirst = uuid("canonical-first");
        UUID relationId = uuid("relation");
        UUID canonicalRelationId = uuid("canonical-relation");
        CanonicalRelation original = new CanonicalRelation(
                relationId, first, second, RelationOrientation.DIRECTED);
        EffectiveGraphCuration curation = EffectiveGraphCuration.from(List.of(
                alias(
                        "entity",
                        GraphIdentityRef.entity(first),
                        GraphIdentityRef.entity(canonicalFirst)),
                alias(
                        "relation",
                        GraphIdentityRef.relation(relationId),
                        GraphIdentityRef.relation(canonicalRelationId))));

        CanonicalRelation effective = curation.effective(original).orElseThrow();
        assertEquals(canonicalFirst, effective.sourceEntityId());
        assertEquals(second, effective.targetEntityId());
        assertEquals(canonicalRelationId, effective.id());
        assertEquals(first, original.sourceEntityId());
        assertEquals(relationId, original.id());
    }

    private static GraphCurationRecord.IdentityAlias alias(
            String id, GraphIdentityRef source, GraphIdentityRef target) {
        return new GraphCurationRecord.IdentityAlias(
                uuid(id), NAMESPACE, source, target, PROVENANCE);
    }

    private static GraphCurationRecord.IdentitySuppression suppression(
            String id, GraphIdentityRef identity) {
        return new GraphCurationRecord.IdentitySuppression(
                uuid(id), NAMESPACE, identity, PROVENANCE);
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(
                value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
