package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.retrieval.GraphEvidenceVerifier;
import com.orgmemory.core.knowledge.retrieval.KnowledgeRetrievalUnavailableException;
import com.orgmemory.core.knowledge.retrieval.VerifiedGraphEvidenceScope;
import com.orgmemory.core.organization.CurrentActor;

/** Shared Graph-side translation for unavailable canonical evidence scopes. */
final class GraphEvidenceScopeAccess {

    private GraphEvidenceScopeAccess() {}

    static VerifiedGraphEvidenceScope verify(
            GraphEvidenceVerifier verifier,
            CurrentActor actor,
            String authorizationModelId,
            String unavailableMessage) {
        try {
            return verifier.verifyScope(actor, authorizationModelId);
        } catch (KnowledgeRetrievalUnavailableException unavailable) {
            throw new KnowledgeRetrievalUnavailableException(
                    unavailableMessage, unavailable);
        }
    }
}
