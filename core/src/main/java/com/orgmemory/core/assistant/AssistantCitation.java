package com.orgmemory.core.assistant;

import com.orgmemory.core.knowledge.RetrievedKnowledgeEvidence;

/**
 * One prompt-scoped citation whose number is assigned by the server when the
 * permission-verified evidence snapshot is rendered.
 */
public record AssistantCitation(
        int number,
        RetrievedKnowledgeEvidence evidence) {

    public AssistantCitation {
        if (number < 1) {
            throw new IllegalArgumentException("citation number must be positive");
        }
        if (evidence == null) {
            throw new IllegalArgumentException("citation evidence is required");
        }
    }
}
