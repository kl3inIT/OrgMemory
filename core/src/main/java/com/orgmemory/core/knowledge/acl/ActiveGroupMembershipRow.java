package com.orgmemory.core.knowledge.acl;

import java.time.Instant;
import java.util.UUID;

/** One member row, or a null member for an empty group, from the canonical active head. */
public record ActiveGroupMembershipRow(
        UUID groupPrincipalId,
        UUID membershipSnapshotId,
        long membershipGeneration,
        Instant sealedAt,
        UUID memberPrincipalId) {
}
