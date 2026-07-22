package com.orgmemory.core.knowledge;

import java.util.List;

public record SecureKnowledgeSearchResult(String requestId, List<RetrievedKnowledgeEvidence> evidence) {

    public SecureKnowledgeSearchResult {
        evidence = List.copyOf(evidence);
    }
}
