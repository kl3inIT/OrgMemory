package com.orgmemory.api.knowledge;

import com.orgmemory.core.knowledge.RetrievedKnowledgeEvidence;
import java.util.UUID;

record KnowledgeEvidenceResponse(
        UUID citationId,
        UUID knowledgeAssetId,
        String title,
        String content,
        String sourceUri,
        Integer startPage,
        Integer endPage,
        String heading,
        double relevanceScore) {

    static KnowledgeEvidenceResponse from(RetrievedKnowledgeEvidence evidence) {
        return new KnowledgeEvidenceResponse(
                evidence.chunkId(),
                evidence.knowledgeAssetId(),
                evidence.title(),
                evidence.content(),
                evidence.sourceUri(),
                evidence.startPage(),
                evidence.endPage(),
                evidence.heading(),
                evidence.relevanceScore());
    }
}
