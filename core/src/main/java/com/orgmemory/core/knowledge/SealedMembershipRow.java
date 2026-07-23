package com.orgmemory.core.knowledge;

import java.time.Instant;
import java.util.UUID;

/** One sealed group-membership row joined to the generation that sealed it. */
record SealedMembershipRow(
        UUID groupPrincipalId,
        UUID memberPrincipalId,
        UUID sourceAclSnapshotId,
        long aclGeneration,
        Instant sealedAt) {
}
