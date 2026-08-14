package com.orgmemory.core.assistant;

import com.orgmemory.core.organization.CurrentActor;
import java.util.Optional;
import java.util.UUID;

public interface AssistantPrivateFileSearch {
    AssistantPrivateFileSearchResult search(
            CurrentActor actor,
            String query,
            Integer requestedLimit,
            String requestId,
            AssistantPrivateFileSelection selection);

    Optional<AssistantPrivateFileCitation> findCitation(
            CurrentActor actor,
            UUID fileId,
            long processingGeneration,
            UUID chunkId);
}
