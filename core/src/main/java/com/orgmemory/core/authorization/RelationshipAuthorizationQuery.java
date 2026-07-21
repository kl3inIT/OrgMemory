package com.orgmemory.core.authorization;

import java.util.Objects;
import java.util.List;

public record RelationshipAuthorizationQuery(
        PrincipalRef principal,
        PermissionKey permission,
        ResourceRef resource,
        List<ContextualRelationship> contextualRelationships) {

    public RelationshipAuthorizationQuery(
            PrincipalRef principal,
            PermissionKey permission,
            ResourceRef resource) {
        this(principal, permission, resource, List.of());
    }

    public RelationshipAuthorizationQuery {
        principal = Objects.requireNonNull(principal, "principal");
        permission = Objects.requireNonNull(permission, "permission");
        resource = Objects.requireNonNull(resource, "resource");
        contextualRelationships = contextualRelationships == null
                ? List.of()
                : List.copyOf(contextualRelationships);
    }
}
