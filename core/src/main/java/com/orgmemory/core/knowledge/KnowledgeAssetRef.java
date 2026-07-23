package com.orgmemory.core.knowledge;

import java.util.UUID;

public record KnowledgeAssetRef(
        UUID knowledgeAssetId,
        UUID knowledgeAssetVersionId,
        UUID normalizedRecordId,
        UUID rawSourceObjectId,
        UUID sourceAclSnapshotId,
        KnowledgeAssetVersionStatus status) {
}
