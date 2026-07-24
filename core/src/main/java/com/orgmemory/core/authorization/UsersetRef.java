package com.orgmemory.core.authorization;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A reference of the form {@code type:id#relation} — everyone holding a relation on an object,
 * rather than one named subject.
 *
 * <p>OpenFGA writes both kinds into the same list, so {@link #parse} answers empty for a plain
 * subject such as {@code user:…} instead of failing. That is the discriminator, not an error.
 */
public record UsersetRef(ResourceRef resource, RelationName relation) {

    public UsersetRef {
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(relation, "relation");
    }

    public static Optional<UsersetRef> parse(UUID organizationId, String value) {
        Objects.requireNonNull(organizationId, "organizationId");
        if (value == null) {
            return Optional.empty();
        }
        int hash = value.indexOf('#');
        int colon = value.indexOf(':');
        if (hash < 0 || colon < 0 || colon > hash) {
            return Optional.empty();
        }
        try {
            return Optional.of(new UsersetRef(
                    new ResourceRef(
                            organizationId,
                            value.substring(0, colon),
                            value.substring(colon + 1, hash)),
                    RelationName.of(value.substring(hash + 1))));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public String key() {
        return resource.openFgaObject() + "#" + relation.value();
    }
}
