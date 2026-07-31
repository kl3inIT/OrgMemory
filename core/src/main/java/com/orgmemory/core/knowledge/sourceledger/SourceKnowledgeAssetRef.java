package com.orgmemory.core.knowledge.sourceledger;

import java.util.UUID;

/** Asset identity retained by source provenance without importing the asset model. */
public record SourceKnowledgeAssetRef(
        UUID knowledgeAssetId,
        UUID knowledgeAssetVersionId,
        UUID normalizedRecordId,
        UUID rawSourceObjectId,
        UUID sourceAclSnapshotId) {
}
