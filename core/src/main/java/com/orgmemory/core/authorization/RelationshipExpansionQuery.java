package com.orgmemory.core.authorization;

import java.util.Objects;

/**
 * Asks which usersets grant {@code relation} on {@code resource}.
 *
 * <p>There is no principal here on purpose. An expansion is about the resource, not about one
 * actor, so the same answer serves "why does this person have access" and "who else does".
 *
 * <p>The relation is a {@link RelationName} rather than a {@link PermissionKey} because walking
 * an explanation descends from a permission into the nouns composing it.
 */
public record RelationshipExpansionQuery(ResourceRef resource, RelationName relation) {

    public RelationshipExpansionQuery {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(relation, "relation");
    }

    public static RelationshipExpansionQuery of(ResourceRef resource, PermissionKey permission) {
        return new RelationshipExpansionQuery(resource, RelationName.of(permission));
    }
}
