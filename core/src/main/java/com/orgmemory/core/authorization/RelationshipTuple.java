package com.orgmemory.core.authorization;

import java.util.Objects;
import java.util.regex.Pattern;

public record RelationshipTuple(String user, String relation, String object) {

    private static final Pattern RELATION_FORMAT = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern USER_REFERENCE_FORMAT =
            Pattern.compile("[a-z][a-z0-9_]*:[^:#\\s]+(?:#[a-z][a-z0-9_]*)?");
    private static final Pattern OBJECT_REFERENCE_FORMAT =
            Pattern.compile("[a-z][a-z0-9_]*:[^:#\\s]+");

    public RelationshipTuple {
        user = requireReference(user, "user", USER_REFERENCE_FORMAT);
        relation = requireRelation(relation);
        object = requireReference(object, "object", OBJECT_REFERENCE_FORMAT);
    }

    public static RelationshipTuple of(String user, String relation, String object) {
        return new RelationshipTuple(user, relation, object);
    }

    private static String requireReference(String value, String field, Pattern format) {
        String normalized = requireText(value, field);
        if (!format.matcher(normalized).matches()) {
            throw new IllegalArgumentException(field + " must be a valid OpenFGA reference");
        }
        return normalized;
    }

    private static String requireRelation(String value) {
        String normalized = requireText(value, "relation");
        if (!RELATION_FORMAT.matcher(normalized).matches() || normalized.startsWith("can_")) {
            throw new IllegalArgumentException("Stored relationships must use a noun relation");
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
