package com.orgmemory.core.knowledge.acl;

import com.orgmemory.core.permission.AccessGate;
import java.time.Instant;
import java.util.UUID;

/** Immutable ACL snapshot facts exposed to source processing. */
public record SourceAclSnapshotRef(
        UUID id,
        UUID rawSourceObjectId,
        long aclGeneration,
        AclCaptureStatus captureStatus,
        AccessGate defaultGate,
        String aclSha256,
        Instant capturedAt,
        Instant validUntil) {

    public boolean isUsableAt(Instant instant) {
        return captureStatus == AclCaptureStatus.COMPLETE
                && validUntil != null
                && validUntil.isAfter(instant);
    }
}
