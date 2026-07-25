package com.orgmemory.graphrag.extraction;

import java.util.List;
import java.util.Objects;

public record ExtractionRoundResponse(
        String assistantMessage,
        List<ExtractionCandidateEntity> entities,
        List<ExtractionCandidateRelation> relations,
        int providerInputTokens,
        int providerOutputTokens) {

    public ExtractionRoundResponse {
        assistantMessage = Objects.requireNonNull(assistantMessage, "assistantMessage");
        if (assistantMessage.isBlank()) {
            throw new IllegalArgumentException("assistantMessage must not be blank");
        }
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
        if (providerInputTokens < 0 || providerOutputTokens < 0) {
            throw new IllegalArgumentException("provider token counts must be non-negative");
        }
    }
}
