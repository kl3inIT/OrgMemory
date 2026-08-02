package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;
import java.util.UUID;

/** Canonical source-owned identity needed to retire or retry retirement of a published upload. */
public record ReadyManualUploadRef(
        UUID sourceId,
        UUID knowledgeAssetId,
        UUID knowledgeAssetVersionId,
        boolean sourceArchived) {

    public ReadyManualUploadRef {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(knowledgeAssetVersionId, "knowledgeAssetVersionId");
    }
}
