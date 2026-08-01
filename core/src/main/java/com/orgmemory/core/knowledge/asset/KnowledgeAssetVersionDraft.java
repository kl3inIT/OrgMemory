package com.orgmemory.core.knowledge.asset;

import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.UUID;

/** Asset-owned immutable facts used to create one pending version. */
record KnowledgeAssetVersionDraft(
        UUID organizationId,
        UUID rawSourceObjectId,
        UUID normalizedRecordId,
        UUID sourceAclSnapshotId,
        UUID departmentId,
        String title,
        String content,
        String language,
        KnowledgeClassification classification,
        DeclaredAccessScope declaredAccess,
        String contentSha256,
        AccessGate orgMemoryGate) {
}
