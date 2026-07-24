package com.orgmemory.core.authorization;

import java.util.Objects;

/**
 * One hop of a derivation: the object reached, the relation held on it, and how it was reached.
 */
public record AccessStep(String object, String relation, AccessStepKind kind) {

    public AccessStep {
        object = requireText(object, "object");
        relation = requireText(relation, "relation");
        Objects.requireNonNull(kind, "kind");
    }

    public enum AccessStepKind {

        /** A tuple names the subject against this relation. */
        DIRECT,

        /** The relation is rewritten to another relation on the same object. */
        COMPUTED,

        /** The relation is inherited from a related object — the {@code X from Y} hop. */
        INHERITED
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
