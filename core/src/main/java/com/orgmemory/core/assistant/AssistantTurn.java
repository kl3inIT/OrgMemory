package com.orgmemory.core.assistant;

import java.util.List;
import reactor.core.publisher.Flux;

public record AssistantTurn(
        String requestId,
        List<AssistantCitation> citations,
        Flux<String> content,
        Flux<AssistantAgentActivity> activities) {

    public AssistantTurn(
            String requestId,
            List<AssistantCitation> citations,
            Flux<String> content) {
        this(requestId, citations, content, Flux.empty());
    }

    public AssistantTurn {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId is required");
        }
        citations = List.copyOf(citations);
        for (int index = 0; index < citations.size(); index++) {
            if (citations.get(index).number() != index + 1) {
                throw new IllegalArgumentException(
                        "citation numbers must be consecutive and ordered");
            }
        }
        if (content == null) {
            throw new IllegalArgumentException("content is required");
        }
        if (activities == null) {
            throw new IllegalArgumentException("activities is required");
        }
    }
}
