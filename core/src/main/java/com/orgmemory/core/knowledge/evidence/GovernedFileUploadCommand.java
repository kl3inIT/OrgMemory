package com.orgmemory.core.knowledge.evidence;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.Objects;
import java.util.UUID;

public record GovernedFileUploadCommand(
        CurrentActor actor,
        String fileName,
        long contentLength,
        KnowledgeClassification classification,
        UUID knowledgeSpaceId) {

    public GovernedFileUploadCommand {
        Objects.requireNonNull(actor, "actor");
    }
}
