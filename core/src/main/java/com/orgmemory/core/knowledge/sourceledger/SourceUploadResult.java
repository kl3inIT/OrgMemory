package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;
import java.util.UUID;

/** Immutable identity returned to governed channels that bind an upload. */
record SourceUploadResult(
        SourceSummary summary,
        UUID sourceRevisionId,
        UUID knowledgeSpaceId) {

    public SourceUploadResult {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
    }
}
