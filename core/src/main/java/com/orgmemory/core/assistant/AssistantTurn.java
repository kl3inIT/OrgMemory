package com.orgmemory.core.assistant;

import com.orgmemory.core.knowledge.RetrievedKnowledgeEvidence;
import java.util.List;
import reactor.core.publisher.Flux;

public record AssistantTurn(
        String requestId,
        List<RetrievedKnowledgeEvidence> evidence,
        Flux<String> content) {

    public AssistantTurn {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        evidence = List.copyOf(evidence);
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
    }
}
