package com.orgmemory.core.assistant;

import com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence;

/**
 * One prompt-scoped citation whose number is assigned by the server when the
 * permission-verified evidence snapshot is rendered.
 */
public record AssistantCitation(
        int number,
        RetrievedKnowledgeEvidence evidence,
        AssistantCitationEvidence privateEvidence) {

    public AssistantCitation(int number, RetrievedKnowledgeEvidence evidence) {
        this(number, evidence, null);
    }

    public AssistantCitation(int number, AssistantCitationEvidence privateEvidence) {
        this(number, null, privateEvidence);
    }

    public AssistantCitation {
        if (number < 1) {
            throw new IllegalArgumentException("citation number must be positive");
        }
        if ((evidence == null) == (privateEvidence == null)) {
            throw new IllegalArgumentException("citation requires exactly one evidence kind");
        }
        if (privateEvidence != null
                && privateEvidence.kind() != AssistantCitationEvidence.Kind.ASSISTANT_FILE) {
            throw new IllegalArgumentException("generalized citations accept private evidence only");
        }
    }

    public AssistantCitationEvidence citationEvidence() {
        return privateEvidence == null
                ? AssistantCitationEvidence.knowledge(evidence)
                : privateEvidence;
    }
}
