package com.orgmemory.core.knowledge.evidence;

import java.util.Objects;
import java.util.UUID;

public record GovernedFileUploadResult(
        UUID sourceObjectId,
        UUID sourceRevisionId,
        UUID knowledgeSpaceId,
        String fileName) {

    public GovernedFileUploadResult {
        Objects.requireNonNull(sourceObjectId, "sourceObjectId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        fileName = Objects.requireNonNull(fileName, "fileName");
    }
}
