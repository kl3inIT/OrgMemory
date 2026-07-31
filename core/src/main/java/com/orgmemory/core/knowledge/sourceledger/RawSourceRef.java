package com.orgmemory.core.knowledge.sourceledger;

import java.util.UUID;

public record RawSourceRef(
        UUID rawSourceObjectId,
        UUID sourceAclSnapshotId,
        RawSourceStatus status) {
}
