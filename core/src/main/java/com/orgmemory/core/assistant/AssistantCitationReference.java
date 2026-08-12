package com.orgmemory.core.assistant;

import java.util.Objects;
import java.util.UUID;

/** Internal replay reference. API adapters must reauthorize before serialization. */
public record AssistantCitationReference(
        int citationNumber,
        AssistantCitationEvidence.Kind kind,
        UUID chunkId,
        UUID assistantFileId,
        Long processingGeneration) {

    public AssistantCitationReference(int citationNumber, UUID chunkId) {
        this(citationNumber, AssistantCitationEvidence.Kind.KNOWLEDGE, chunkId, null, null);
    }

    public AssistantCitationReference {
        if (citationNumber < 1 || citationNumber > 100) {
            throw new IllegalArgumentException("citationNumber must be between 1 and 100");
        }
        Objects.requireNonNull(chunkId, "chunkId");
        Objects.requireNonNull(kind, "kind");
        if (kind == AssistantCitationEvidence.Kind.ASSISTANT_FILE
                && (assistantFileId == null || processingGeneration == null || processingGeneration <= 0)) {
            throw new IllegalArgumentException("private citation requires file identity and generation");
        }
    }
}
