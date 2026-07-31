package com.orgmemory.core.authorization;

import static com.orgmemory.core.shared.Texts.requireText;

import java.util.Objects;

/**
 * One branch that did not grant access, and why.
 *
 * <p>{@link Kind#EXPLICIT_DENY} is not a stronger form of {@link Kind#NO_RELATIONSHIP}. A missing
 * relationship is fixed by granting one; an explicit deny is a decision somebody made, and where
 * the ACL authority is the source system, it cannot be fixed in OrgMemory at all.
 */
public record AccessBlock(String branch, Kind kind, String detail) {

    public AccessBlock {
        branch = requireText(branch, "branch");
        Objects.requireNonNull(kind, "kind");
        detail = detail == null ? "" : detail.trim();
    }

    public enum Kind {
        NO_RELATIONSHIP,
        EXPLICIT_DENY
    }

    public static AccessBlock missing(String branch, String detail) {
        return new AccessBlock(branch, Kind.NO_RELATIONSHIP, detail);
    }

    public static AccessBlock denied(String branch, String detail) {
        return new AccessBlock(branch, Kind.EXPLICIT_DENY, detail);
    }

}
