package com.orgmemory.core.authorization;

import java.util.Objects;

/**
 * Optional server-side filters for an OpenFGA tuple read.
 *
 * <p>Administrative reads must narrow the tuple store at the authorization
 * service rather than downloading every tenant's relationships and filtering
 * them in application memory.
 */
public record RelationshipTupleFilter(String user, String relation, String object) {

    public RelationshipTupleFilter {
        user = normalize(user);
        relation = normalize(relation);
        object = normalize(object);
        if (user == null && relation == null && object == null) {
            throw new IllegalArgumentException("At least one tuple filter is required");
        }
    }

    public static RelationshipTupleFilter object(String object) {
        return new RelationshipTupleFilter(null, null, Objects.requireNonNull(object, "object"));
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
