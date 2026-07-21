package com.orgmemory.core.knowledge;

import java.util.UUID;

public record RawSourceRef(
        UUID rawSourceObjectId,
        UUID sourceAclSnapshotId,
        RawSourceStatus status) {
}
