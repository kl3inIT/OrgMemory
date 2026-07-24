package com.orgmemory.core.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccessExplanationServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID UNIT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID SPACE_ID = UUID.fromString("88888888-8888-4888-8888-888888888802");
    private static final UUID MINH_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final PrincipalRef MINH = PrincipalRef.user(MINH_ID);
    private static final PermissionKey CAN_PUBLISH = PermissionKey.of("can_publish");
    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final String POLICY = "01KY4JS83SQ11R9RMKAMYCPH2Q";

    private final ResourceRef space = ResourceRef.of(ORGANIZATION_ID, "knowledge_space", SPACE_ID);

    private RelationshipAuthorizationPort relationships;
    private RelationshipExpansionPort expansion;
    private AccessExplanationService service;

    @BeforeEach
    void setUp() {
        relationships = mock(RelationshipAuthorizationPort.class);
        expansion = mock(RelationshipExpansionPort.class);
        service = new AccessExplanationService(relationships, expansion, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void grantedAccessReportsTheDerivationThatReachedThePrincipal() {
        allow();
        expandsTo(space, "can_publish", union(
                "knowledge_space:" + SPACE_ID + "#can_publish",
                direct("knowledge_space:" + SPACE_ID + "#reviewer", "organizational_unit:" + UNIT_ID + "#manager")));
        expandsTo(
                ResourceRef.of(ORGANIZATION_ID, "organizational_unit", UNIT_ID),
                "manager",
                direct("organizational_unit:" + UNIT_ID + "#manager", MINH.openFgaUser()));

        var explanation = service.explain(ORGANIZATION_ID, MINH, CAN_PUBLISH, space);

        assertEquals(AccessState.ALLOWED, explanation.state());
        assertEquals(
                List.of(
                        new AccessStep(
                                "knowledge_space:" + SPACE_ID, "reviewer", AccessStep.AccessStepKind.DIRECT),
                        new AccessStep(
                                "organizational_unit:" + UNIT_ID, "manager", AccessStep.AccessStepKind.DIRECT)),
                explanation.path());
        assertTrue(explanation.blockedBy().isEmpty());
    }

    @Test
    void aUnionStopsAtTheFirstBranchThatGrants() {
        allow();
        expandsTo(space, "can_publish", union(
                "knowledge_space:" + SPACE_ID + "#can_publish",
                direct("knowledge_space:" + SPACE_ID + "#reviewer", MINH.openFgaUser()),
                direct("knowledge_space:" + SPACE_ID + "#administrator", MINH.openFgaUser())));

        var explanation = service.explain(ORGANIZATION_ID, MINH, CAN_PUBLISH, space);

        assertEquals(1, explanation.path().size());
        assertEquals("reviewer", explanation.path().getFirst().relation());
    }

    @Test
    void refusedAccessNamesEveryBranchThatWasEvaluatedAndCarriesNoPath() {
        when(relationships.check(any())).thenReturn(AuthorizationDecision.deny("RELATIONSHIP_DENIED", POLICY));
        expandsTo(space, "can_publish", union(
                "knowledge_space:" + SPACE_ID + "#can_publish",
                direct("knowledge_space:" + SPACE_ID + "#reviewer", "user:someone-else"),
                new ExpansionNode.TupleToUserset(
                        "knowledge_space:" + SPACE_ID + "#can_manage_acl",
                        "knowledge_space:" + SPACE_ID + "#organization",
                        List.of("organization:" + ORGANIZATION_ID + "#administrator"))));

        var explanation = service.explain(ORGANIZATION_ID, MINH, CAN_PUBLISH, space);

        assertEquals(AccessState.DENIED, explanation.state());
        assertTrue(explanation.path().isEmpty());
        assertEquals(2, explanation.blockedBy().size());
        assertTrue(explanation.blockedBy().stream()
                .allMatch(block -> block.kind() == AccessBlock.Kind.NO_RELATIONSHIP));
    }

    @Test
    void anExpiredMirroredAclIsUnknownEvenWhenTheEngineStillAllows() {
        allow();
        var provenance = AclProvenance.source(
                "Slack #ke-hoach", 18L, NOW.minusSeconds(90_000), NOW.minusSeconds(3_600), NOW);

        var explanation = service.explain(ORGANIZATION_ID, MINH, CAN_PUBLISH, space, provenance);

        assertEquals(AccessState.UNKNOWN, explanation.state());
        assertEquals("ACL_VALIDITY_EXPIRED", explanation.reasonCode());
        assertTrue(explanation.path().isEmpty());
        assertTrue(explanation.provenance().mirrored());
    }

    @Test
    void aMirroredAclInsideItsValidityStillAnswers() {
        allow();
        expandsTo(space, "can_publish", direct("knowledge_space:" + SPACE_ID + "#reviewer", MINH.openFgaUser()));
        var provenance = AclProvenance.source(
                "Slack #ke-hoach", 18L, NOW.minusSeconds(3_600), NOW.plusSeconds(3_600), NOW);

        var explanation = service.explain(ORGANIZATION_ID, MINH, CAN_PUBLISH, space, provenance);

        assertEquals(AccessState.ALLOWED, explanation.state());
        assertFalse(explanation.provenance().expired());
    }

    @Test
    void anUnansweredCheckIsUnknownRatherThanRefused() {
        when(relationships.check(any()))
                .thenReturn(AuthorizationDecision.indeterminate("OPENFGA_TIMEOUT", POLICY));

        var explanation = service.explain(ORGANIZATION_ID, MINH, CAN_PUBLISH, space);

        assertEquals(AccessState.UNKNOWN, explanation.state());
        assertEquals("OPENFGA_TIMEOUT", explanation.reasonCode());
    }

    @Test
    void anUnreadableExpansionLeavesAGrantWithoutAPathRatherThanInventingOne() {
        allow();
        when(expansion.expand(any()))
                .thenReturn(RelationshipExpansionResult.indeterminate("OPENFGA_UNAVAILABLE", POLICY));

        var explanation = service.explain(ORGANIZATION_ID, MINH, CAN_PUBLISH, space);

        assertEquals(AccessState.ALLOWED, explanation.state());
        assertEquals("GRANTED_PATH_UNAVAILABLE", explanation.reasonCode());
        assertTrue(explanation.path().isEmpty());
    }

    @Test
    void effectivePermissionsReportUnknownWhenTheEngineDoesNotAnswer() {
        when(relationships.check(any()))
                .thenReturn(AuthorizationDecision.allow(POLICY))
                .thenReturn(AuthorizationDecision.deny("RELATIONSHIP_DENIED", POLICY))
                .thenReturn(AuthorizationDecision.indeterminate("OPENFGA_TIMEOUT", POLICY));

        var states = service.effectivePermissions(
                ORGANIZATION_ID,
                MINH,
                List.of(
                        PermissionKey.of("can_search_knowledge"),
                        PermissionKey.of("can_manage_members"),
                        PermissionKey.of("can_view_audit")),
                ResourceRef.of(ORGANIZATION_ID, "organization", ORGANIZATION_ID));

        assertEquals(AccessState.ALLOWED, states.get(PermissionKey.of("can_search_knowledge")));
        assertEquals(AccessState.DENIED, states.get(PermissionKey.of("can_manage_members")));
        assertEquals(AccessState.UNKNOWN, states.get(PermissionKey.of("can_view_audit")));
    }

    private void allow() {
        when(relationships.check(any())).thenReturn(AuthorizationDecision.allow(POLICY));
    }

    private void expandsTo(ResourceRef resource, String relation, ExpansionNode root) {
        when(expansion.expand(
                        new RelationshipExpansionQuery(resource, RelationName.of(relation))))
                .thenReturn(RelationshipExpansionResult.resolved(root, POLICY));
    }

    private static ExpansionNode direct(String name, String... users) {
        return new ExpansionNode.Direct(name, List.of(users));
    }

    private static ExpansionNode union(String name, ExpansionNode... children) {
        return new ExpansionNode.Union(name, List.of(children));
    }
}
