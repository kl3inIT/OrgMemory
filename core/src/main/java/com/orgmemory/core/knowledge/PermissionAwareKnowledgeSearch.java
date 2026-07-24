package com.orgmemory.core.knowledge;

import com.orgmemory.core.organization.CurrentActor;

/** One authorization-equivalent knowledge retrieval contract for UI and MCP. */
public interface PermissionAwareKnowledgeSearch {

    SecureKnowledgeSearchResult search(
            CurrentActor actor,
            String query,
            Integer requestedLimit,
            String requestId);
}
