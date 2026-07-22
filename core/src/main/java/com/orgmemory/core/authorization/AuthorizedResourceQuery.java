package com.orgmemory.core.authorization;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public record AuthorizedResourceQuery(
        UUID organizationId,
        PrincipalRef principal,
        PermissionKey permission,
        String resourceType) {

    private static final Pattern TYPE_FORMAT = Pattern.compile("[a-z][a-z0-9_]*");

    public AuthorizedResourceQuery {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(principal, "principal");
        Objects.requireNonNull(permission, "permission");
        resourceType = Objects.requireNonNull(resourceType, "resourceType").trim();
        if (!TYPE_FORMAT.matcher(resourceType).matches()) {
            throw new IllegalArgumentException("Resource type must be a singular lowercase OpenFGA type");
        }
    }
}
