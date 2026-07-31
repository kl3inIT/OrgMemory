package com.orgmemory.core.knowledge.space;

import java.util.Objects;
import java.util.UUID;

/**
 * Who a Knowledge Space grant is written for.
 *
 * <p>Deliberately a closed set rather than a free-form OpenFGA user reference. A reference typed
 * by hand can name another organization — {@code organization:<other>#member} as a viewer would
 * hand a second tenant a grant on this one's space — and validating an arbitrary string after the
 * fact means enumerating every shape it could take. Naming the four shapes the product offers
 * makes the tenant check a field comparison instead.
 *
 * <p>Reading grants back does not use this type. Bootstrap tuples carry references outside these
 * four shapes, so a listing reports what is stored rather than only what this can express.
 */
public record KnowledgeSpaceSubject(Kind kind, UUID id, String role) {

    public enum Kind {
        /** Everybody in the organization. */
        ORGANIZATION,
        /** Everybody in one organizational unit. */
        DEPARTMENT,
        /**
         * Whoever manages one organizational unit.
         *
         * <p>Separate from {@link #DEPARTMENT} because the model separates them: reviewing accepts
         * {@code organizational_unit#manager} and not {@code #member}. Without this shape the
         * product could not express a grant its own bootstrap file already contains.
         */
        DEPARTMENT_MANAGERS,
        /** Everybody currently assigned a named role. */
        ROLE,
        /** One named person. */
        USER
    }

    public KnowledgeSpaceSubject {
        Objects.requireNonNull(kind, "kind");
        switch (kind) {
            case DEPARTMENT, DEPARTMENT_MANAGERS, USER -> {
                Objects.requireNonNull(id, "id");
                role = null;
            }
            case ROLE -> {
                role = requireRole(role);
                id = null;
            }
            case ORGANIZATION -> {
                id = null;
                role = null;
            }
        }
    }

    public static KnowledgeSpaceSubject organization() {
        return new KnowledgeSpaceSubject(Kind.ORGANIZATION, null, null);
    }

    public static KnowledgeSpaceSubject department(UUID departmentId) {
        return new KnowledgeSpaceSubject(Kind.DEPARTMENT, departmentId, null);
    }

    public static KnowledgeSpaceSubject departmentManagers(UUID departmentId) {
        return new KnowledgeSpaceSubject(Kind.DEPARTMENT_MANAGERS, departmentId, null);
    }

    public static KnowledgeSpaceSubject role(String role) {
        return new KnowledgeSpaceSubject(Kind.ROLE, null, role);
    }

    public static KnowledgeSpaceSubject user(UUID userId) {
        return new KnowledgeSpaceSubject(Kind.USER, userId, null);
    }

    /**
     * The OpenFGA user reference for this subject.
     *
     * <p>{@code organizationId} is the caller's own organization, never a value from the request,
     * so a subject cannot name a tenant the caller does not belong to.
     */
    String openFgaUser(UUID organizationId) {
        Objects.requireNonNull(organizationId, "organizationId");
        return switch (kind) {
            case ORGANIZATION -> "organization:" + organizationId + "#member";
            case DEPARTMENT -> "organizational_unit:" + id + "#member";
            case DEPARTMENT_MANAGERS -> "organizational_unit:" + id + "#manager";
            case ROLE -> "role:" + role + "#assignee";
            case USER -> "user:" + id;
        };
    }

    private static String requireRole(String value) {
        String normalized = Objects.requireNonNull(value, "role").trim();
        if (normalized.isEmpty() || normalized.indexOf(':') >= 0 || normalized.indexOf('#') >= 0) {
            throw new IllegalArgumentException("Role must be non-empty and must not contain ':' or '#'");
        }
        return normalized;
    }
}
