package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.ai.ChatGenerationRequest;
import java.util.List;
import java.util.Objects;

/**
 * Final model input and citations derived from one permission-verified
 * grounding set.
 */
public record VerifiedKnowledgeGrounding(
        ChatGenerationRequest generationRequest,
        List<RetrievedKnowledgeEvidence> citations,
        int evidenceClosureSize,
        int contextTokens) {

    public VerifiedKnowledgeGrounding {
        Objects.requireNonNull(generationRequest, "generationRequest");
        citations = List.copyOf(Objects.requireNonNull(citations, "citations"));
        if (citations.isEmpty()) {
            throw new IllegalArgumentException(
                    "verified grounding requires citations");
        }
        if (evidenceClosureSize < citations.size()) {
            throw new IllegalArgumentException(
                    "evidence closure cannot be smaller than citations");
        }
        if (contextTokens <= 0) {
            throw new IllegalArgumentException(
                    "contextTokens must be positive");
        }
    }
}
