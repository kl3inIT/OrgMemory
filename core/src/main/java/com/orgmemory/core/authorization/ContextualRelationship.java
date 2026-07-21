package com.orgmemory.core.authorization;

import java.util.Objects;

/**
 * A relationship derived from the current transactional record and supplied only for one check.
 * It is not persisted by the authorization provider.
 */
public record ContextualRelationship(String user, String relation, String object) {

    public ContextualRelationship {
        user = requireReference(user, "user");
        relation = requireText(relation, "relation");
        object = requireReference(object, "object");
    }

    public static ContextualRelationship of(String user, String relation, String object) {
        return new ContextualRelationship(user, relation, object);
    }

    private static String requireReference(String value, String field) {
        String normalized = requireText(value, field);
        if (normalized.indexOf(':') <= 0) {
            throw new IllegalArgumentException(field + " must be an OpenFGA type:id reference");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
