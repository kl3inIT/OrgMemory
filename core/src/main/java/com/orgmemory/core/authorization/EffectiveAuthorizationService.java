package com.orgmemory.core.authorization;

import java.util.Objects;
import java.util.List;
import java.util.UUID;

public class EffectiveAuthorizationService {

    private static final String LOCAL_POLICY_VERSION = "orgmemory-boundary-v1";

    private final RelationshipAuthorizationPort relationships;

    public EffectiveAuthorizationService(RelationshipAuthorizationPort relationships) {
        this.relationships = relationships;
    }

    public AuthorizationDecision authorize(
            UUID actorOrganizationId,
            PrincipalRef principal,
            PermissionKey permission,
            ResourceRef resource) {
        return authorize(actorOrganizationId, principal, permission, resource, List.of());
    }

    public AuthorizationDecision authorize(
            UUID actorOrganizationId,
            PrincipalRef principal,
            PermissionKey permission,
            ResourceRef resource,
            List<ContextualRelationship> contextualRelationships) {
        if (!Objects.equals(actorOrganizationId, resource.organizationId())) {
            return AuthorizationDecision.deny("ORGANIZATION_MISMATCH", LOCAL_POLICY_VERSION);
        }

        try {
            AuthorizationDecision decision = relationships.check(
                    new RelationshipAuthorizationQuery(
                            principal,
                            permission,
                            resource,
                            contextualRelationships));
            return decision.allowed()
                    ? decision
                    : AuthorizationDecision.deny(decision.reasonCode(), decision.policyVersion());
        } catch (RuntimeException exception) {
            return AuthorizationDecision.deny("AUTHORIZATION_ENGINE_UNAVAILABLE", LOCAL_POLICY_VERSION);
        }
    }
}
