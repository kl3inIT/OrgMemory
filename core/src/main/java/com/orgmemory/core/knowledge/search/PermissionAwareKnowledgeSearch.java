package com.orgmemory.core.knowledge.search;

import com.orgmemory.core.organization.CurrentActor;

/** One authorization-equivalent knowledge retrieval contract for UI and MCP. */
public interface PermissionAwareKnowledgeSearch {

    SecureKnowledgeSearchResult search(
            CurrentActor actor,
            String query,
            Integer requestedLimit,
            String requestId);

    /**
     * Applies an optional caller-supplied narrowing ceiling. Implementations
     * must override restricted selection; silently broadening it to ordinary
     * organizational search would be an authorization-boundary failure.
     */
    default SecureKnowledgeSearchResult search(
            CurrentActor actor,
            String query,
            Integer requestedLimit,
            String requestId,
            KnowledgeEvidenceSelection selection) {
        KnowledgeEvidenceSelection required = selection == null
                ? KnowledgeEvidenceSelection.unrestricted()
                : selection;
        if (required.restricted()) {
            throw new IllegalStateException("selected evidence is not supported by this retrieval engine");
        }
        return search(actor, query, requestedLimit, requestId);
    }
}
