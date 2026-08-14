package com.orgmemory.core.assistant;

import java.util.List;

public record AssistantPrivateFileSearchResult(
        String requestId,
        List<AssistantCitationEvidence> evidence) {
    public AssistantPrivateFileSearchResult {
        evidence = List.copyOf(evidence == null ? List.of() : evidence);
    }
}
