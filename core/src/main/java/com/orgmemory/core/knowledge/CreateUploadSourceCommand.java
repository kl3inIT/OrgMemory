package com.orgmemory.core.knowledge;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;

public record CreateUploadSourceCommand(
        CurrentActor actor,
        String fileName,
        String mediaType,
        long contentLength,
        KnowledgeClassification classification) {
}
