package com.orgmemory.core.knowledge.retrieval;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record SecureKnowledgeSearchResult(
        String requestId,
        List<RetrievedKnowledgeEvidence> evidence,
        Optional<VerifiedKnowledgeGrounding> grounding) {

    public SecureKnowledgeSearchResult {
        requestId = Objects.requireNonNull(requestId, "requestId");
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        grounding = Objects.requireNonNull(grounding, "grounding");
        List<RetrievedKnowledgeEvidence> verifiedEvidence = evidence;
        grounding.ifPresent(value -> {
            if (!value.citations().equals(verifiedEvidence)) {
                throw new IllegalArgumentException(
                        "grounding citations must match search evidence");
            }
        });
    }

    public SecureKnowledgeSearchResult(
            String requestId,
            List<RetrievedKnowledgeEvidence> evidence) {
        this(requestId, evidence, Optional.empty());
    }
}
