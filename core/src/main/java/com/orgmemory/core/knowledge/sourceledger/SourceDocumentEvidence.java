package com.orgmemory.core.knowledge.sourceledger;

import java.util.Objects;
import java.util.UUID;

/** Immutable Source Ledger evidence required to open one current document. */
public record SourceDocumentEvidence(
        UUID sourceRevisionId,
        UUID knowledgeAssetId,
        UUID embeddingProfileId,
        SourceCitationEvidence evidence) {

    public SourceDocumentEvidence {
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        Objects.requireNonNull(evidence, "evidence");
    }
}
