package com.orgmemory.core.knowledge.acl;

import java.util.UUID;

/** Current ACL pointer for one canonical source identity. */
public record SourceAclHeadRef(
        UUID currentRawSourceObjectId,
        UUID currentSnapshotId,
        long aclGeneration) {
}
