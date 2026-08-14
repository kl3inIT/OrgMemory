package com.orgmemory.core.assistant;

import com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence;
import java.util.Objects;
import java.util.UUID;

public record AssistantCitationEvidence(
        Kind kind,
        UUID chunkId,
        UUID assistantFileId,
        Long processingGeneration,
        String title,
        String content,
        String sourceUri,
        Integer startPage,
        Integer endPage,
        String heading) {

    public AssistantCitationEvidence {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(chunkId, "chunkId");
        title = Objects.requireNonNull(title, "title");
        content = Objects.requireNonNull(content, "content");
        sourceUri = sourceUri == null ? null : sourceUri.strip();
        if (kind == Kind.KNOWLEDGE
                && (assistantFileId != null || processingGeneration != null)) {
            throw new IllegalArgumentException("knowledge evidence cannot carry a private file identity");
        }
        if (kind == Kind.ASSISTANT_FILE
                && (assistantFileId == null || processingGeneration == null || processingGeneration <= 0)) {
            throw new IllegalArgumentException("private evidence requires file identity and generation");
        }
    }

    public static AssistantCitationEvidence knowledge(RetrievedKnowledgeEvidence evidence) {
        return new AssistantCitationEvidence(
                Kind.KNOWLEDGE,
                evidence.chunkId(),
                null,
                null,
                evidence.title(),
                evidence.content(),
                evidence.sourceUri(),
                evidence.startPage(),
                evidence.endPage(),
                evidence.heading());
    }

    public enum Kind { KNOWLEDGE, ASSISTANT_FILE }
}
