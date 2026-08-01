package com.orgmemory.api.knowledge;

import com.orgmemory.core.knowledge.search.SecureKnowledgeSearchResult;
import java.util.List;

record KnowledgeSearchResponse(String requestId, List<KnowledgeEvidenceResponse> evidence) {

    static KnowledgeSearchResponse from(SecureKnowledgeSearchResult result) {
        return new KnowledgeSearchResponse(
                result.requestId(),
                result.evidence().stream().map(KnowledgeEvidenceResponse::from).toList());
    }
}
