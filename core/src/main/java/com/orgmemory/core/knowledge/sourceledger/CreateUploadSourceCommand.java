package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.UUID;

public record CreateUploadSourceCommand(
        CurrentActor actor,
        String fileName,
        long contentLength,
        KnowledgeClassification classification,
        UUID knowledgeSpaceId) {
}
