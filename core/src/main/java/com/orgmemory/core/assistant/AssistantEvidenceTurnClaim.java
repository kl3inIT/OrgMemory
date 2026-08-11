package com.orgmemory.core.assistant;

import com.orgmemory.core.knowledge.search.KnowledgeEvidenceSelection;
import java.util.Objects;

public record AssistantEvidenceTurnClaim(
        AssistantTurnRef turn,
        KnowledgeEvidenceSelection selection) {

    public AssistantEvidenceTurnClaim {
        Objects.requireNonNull(turn, "turn");
        Objects.requireNonNull(selection, "selection");
    }
}
