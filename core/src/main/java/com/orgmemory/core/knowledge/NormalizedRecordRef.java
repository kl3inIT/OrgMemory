package com.orgmemory.core.knowledge;

import java.util.UUID;

public record NormalizedRecordRef(
        UUID normalizedRecordId,
        UUID rawSourceObjectId,
        UUID sourceAclSnapshotId,
        NormalizedRecordStatus status,
        NormalizationIssue issue) {
}
