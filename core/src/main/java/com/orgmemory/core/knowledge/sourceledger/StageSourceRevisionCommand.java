package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.acl.AclAuthority;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.UUID;

/** Source Ledger-owned command for staging connector evidence and a source revision. */
public record StageSourceRevisionCommand(
        UUID organizationId,
        UUID knowledgeSpaceId,
        UUID createdByUserId,
        AclAuthority aclAuthority,
        String sourceSystem,
        String sourceConnectionKey,
        String externalObjectId,
        String title,
        KnowledgeClassification classification,
        DeclaredAccessScope declaredAccess,
        UUID proposedSourceObjectId,
        UUID sourceRevisionId,
        UUID evidenceBlobId,
        StoredObject storedObject) {}
