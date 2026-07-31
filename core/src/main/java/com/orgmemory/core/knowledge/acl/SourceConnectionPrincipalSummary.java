package com.orgmemory.core.knowledge.acl;

import java.time.Instant;

/** ACL-owned summary used to tally observed principals by source connection. */
public record SourceConnectionPrincipalSummary(
        String sourceSystem,
        String sourceConnectionKey,
        SourcePrincipalKind kind,
        boolean mapped,
        Instant lastSeenAt) {
}
