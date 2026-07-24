package com.orgmemory.core.authorization;

import java.time.Instant;
import java.util.Objects;

/**
 * Where a verdict's ACL came from and how current it is.
 *
 * <p>A verdict without this is unsafe to act on. When {@code authority} is {@code SOURCE} the
 * answer is a mirror of a decision Slack or Drive owns: OrgMemory cannot change it, and it was
 * true as of {@code capturedAt} rather than now. Surfaces must say so rather than presenting a
 * copied fact as the live one.
 */
public record AclProvenance(
        String authority,
        String origin,
        Long generation,
        Instant capturedAt,
        Instant validUntil,
        boolean expired) {

    public AclProvenance {
        authority = requireText(authority, "authority");
        origin = origin == null ? "" : origin.trim();
    }

    /** OrgMemory decides this itself, so there is nothing mirrored and nothing to expire. */
    public static AclProvenance orgMemory() {
        return new AclProvenance("ORGMEMORY", "", null, null, null, false);
    }

    public static AclProvenance source(
            String origin, long generation, Instant capturedAt, Instant validUntil, Instant now) {
        return new AclProvenance(
                "SOURCE",
                origin,
                generation,
                capturedAt,
                validUntil,
                validUntil != null && Objects.requireNonNull(now, "now").isAfter(validUntil));
    }

    public boolean mirrored() {
        return "SOURCE".equals(authority);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
