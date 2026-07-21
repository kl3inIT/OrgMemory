package com.orgmemory.core.knowledge;

import java.util.UUID;

public record SourceAclRotationRef(
        UUID rawSourceObjectId,
        UUID sourceAclSnapshotId,
        long aclGeneration,
        AclCaptureStatus captureStatus) {
}
