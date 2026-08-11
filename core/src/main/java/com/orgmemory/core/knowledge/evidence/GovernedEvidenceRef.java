package com.orgmemory.core.knowledge.evidence;

import java.util.Objects;
import java.util.UUID;

public record GovernedEvidenceRef(
        UUID organizationId,
        UUID knowledgeSpaceId,
        UUID sourceObjectId,
        UUID sourceRevisionId,
        ProcessingState processingState,
        boolean sourceActive,
        boolean latestRevision,
        boolean currentRevision,
        UUID knowledgeAssetId,
        UUID knowledgeAssetVersionId,
        String title,
        String fileName,
        String failureCode) {

    public GovernedEvidenceRef {
        Objects.requireNonNull(organizationId, "organizationId");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        Objects.requireNonNull(sourceObjectId, "sourceObjectId");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(processingState, "processingState");
        title = Objects.requireNonNull(title, "title");
        fileName = Objects.requireNonNull(fileName, "fileName");
    }

    public boolean readyAndCurrent() {
        return sourceActive
                && latestRevision
                && currentRevision
                && processingState == ProcessingState.READY
                && knowledgeAssetId != null
                && knowledgeAssetVersionId != null;
    }

    public enum ProcessingState {
        PROCESSING,
        READY,
        FAILED,
        QUARANTINED
    }
}
