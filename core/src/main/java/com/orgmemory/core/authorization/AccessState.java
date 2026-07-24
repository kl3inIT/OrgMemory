package com.orgmemory.core.authorization;

/**
 * What an administrator may conclude about one access question.
 *
 * <p>Two states would be a falsehood here. Most of the ACL OrgMemory evaluates is mirrored from
 * Slack or Drive under a bounded validity, and an expired mirror is not a denial — it is an
 * answer nobody currently holds. Collapsing {@link #UNKNOWN} into {@link #DENIED} would report a
 * refusal that the source never made.
 */
public enum AccessState {

    /** A relationship grants it, and the governing ACL is within its validity. */
    ALLOWED,

    /** No relationship grants it, or an explicit deny withdraws it. */
    DENIED,

    /** The governing ACL is expired, incomplete, or unsupported, so no answer is current. */
    UNKNOWN
}
