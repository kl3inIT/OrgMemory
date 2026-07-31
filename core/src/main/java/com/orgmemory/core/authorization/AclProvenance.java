package com.orgmemory.core.authorization;

import static com.orgmemory.core.shared.Texts.requireText;

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

    /**
     * A mirrored ACL with no recorded validity counts as expired.
     *
     * <p>Absent validity is missing evidence, not licence to assume the copy is current, and a
     * mirror OrgMemory cannot date is exactly what {@code UNKNOWN} exists for. The boundary
     * instant expires with it: at {@code validUntil} the window has closed, so the comparison is
     * "not before" rather than "after".
     */
    public static AclProvenance source(
            String origin, long generation, Instant capturedAt, Instant validUntil, Instant now) {
        Objects.requireNonNull(now, "now");
        return new AclProvenance(
                "SOURCE",
                origin,
                generation,
                capturedAt,
                validUntil,
                validUntil == null || !now.isBefore(validUntil));
    }

    public boolean mirrored() {
        return "SOURCE".equals(authority);
    }

}
