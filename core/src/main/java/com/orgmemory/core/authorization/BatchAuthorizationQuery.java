package com.orgmemory.core.authorization;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record BatchAuthorizationQuery(
        UUID organizationId,
        PrincipalRef principal,
        PermissionKey permission,
        List<ResourceRef> resources) {

    public BatchAuthorizationQuery {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(permission, "permission");
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("At least one resource is required");
        }
        if (new HashSet<>(resources).size() != resources.size()) {
            throw new IllegalArgumentException("Batch resources must be unique");
        }
        for (ResourceRef resource : resources) {
            if (!organizationId.equals(resource.organizationId())) {
                throw new IllegalArgumentException("Every resource must belong to the query organization");
            }
        }
    }
}
