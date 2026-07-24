package com.orgmemory.core.authorization;

import java.util.Objects;

/**
 * Asks which usersets grant {@code permission} on {@code resource}.
 *
 * <p>There is no principal here on purpose. An expansion is about the resource, not about one
 * actor, so the same answer serves "why does this person have access" and "who else does".
 */
public record RelationshipExpansionQuery(ResourceRef resource, PermissionKey permission) {

    public RelationshipExpansionQuery {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(permission, "permission");
    }
}
