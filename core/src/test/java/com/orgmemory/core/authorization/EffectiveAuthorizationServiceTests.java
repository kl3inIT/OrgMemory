package com.orgmemory.core.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class EffectiveAuthorizationServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final PrincipalRef LAURA = PrincipalRef.user(
            UUID.fromString("22222222-2222-2222-2222-222222222222"));
    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");

    @Test
    void allowsOnlyAnExplicitRelationshipAllow() {
        var service = new EffectiveAuthorizationService(query -> AuthorizationDecision.allow("model-1"));

        AuthorizationDecision decision = service.authorize(
                ORGANIZATION_ID,
                LAURA,
                CAN_VIEW,
                ResourceRef.of(ORGANIZATION_ID, "knowledge_asset", UUID.randomUUID()));

        assertTrue(decision.allowed());
        assertEquals("model-1", decision.policyVersion());
    }

    @Test
    void organizationMismatchNeverCallsTheAuthorizationEngine() {
        var service = new EffectiveAuthorizationService(query -> {
            throw new AssertionError("relationship engine must not be called across organizations");
        });

        AuthorizationDecision decision = service.authorize(
                UUID.randomUUID(),
                LAURA,
                CAN_VIEW,
                ResourceRef.of(ORGANIZATION_ID, "knowledge_asset", UUID.randomUUID()));

        assertFalse(decision.allowed());
        assertEquals("ORGANIZATION_MISMATCH", decision.reasonCode());
    }

    @Test
    void indeterminateAndEngineFailuresFailClosed() {
        var indeterminate = new EffectiveAuthorizationService(query ->
                AuthorizationDecision.indeterminate("OPENFGA_TIMEOUT", "model-1"));
        var unavailable = new EffectiveAuthorizationService(query -> {
            throw new IllegalStateException("network failure");
        });
        ResourceRef resource = ResourceRef.of(ORGANIZATION_ID, "knowledge_asset", UUID.randomUUID());

        AuthorizationDecision first = indeterminate.authorize(
                ORGANIZATION_ID, LAURA, CAN_VIEW, resource);
        AuthorizationDecision second = unavailable.authorize(
                ORGANIZATION_ID, LAURA, CAN_VIEW, resource);

        assertFalse(first.allowed());
        assertEquals(AuthorizationOutcome.DENY, first.outcome());
        assertEquals("OPENFGA_TIMEOUT", first.reasonCode());
        assertFalse(second.allowed());
        assertEquals("AUTHORIZATION_ENGINE_UNAVAILABLE", second.reasonCode());
    }
}
