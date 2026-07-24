package com.orgmemory.core.knowledge;

import com.orgmemory.graphrag.query.LightRagQueryRequest;
import com.orgmemory.graphrag.query.SecureContextBudget;
import java.util.Objects;

/**
 * Deployment policy for the LightRAG application use case. Query semantics
 * stay in graph-rag-core; the Spring runtime owns bounded operational limits.
 */
public record GraphRagRetrievalPolicy(
        int maximumKnowledgeSpaces,
        int topK,
        int chunkTopK,
        int relatedChunkNumber,
        int maximumGraphDepth,
        double minimumVectorSimilarity,
        boolean includeHeadings,
        SecureContextBudget contextBudget) {

    public GraphRagRetrievalPolicy {
        if (maximumKnowledgeSpaces <= 0
                || topK <= 0
                || chunkTopK <= 0
                || relatedChunkNumber <= 0
                || maximumGraphDepth < 0) {
            throw new IllegalArgumentException(
                    "GraphRAG retrieval limits must be positive and bounded");
        }
        if (!Double.isFinite(minimumVectorSimilarity)
                || minimumVectorSimilarity < -1.0
                || minimumVectorSimilarity > 1.0) {
            throw new IllegalArgumentException(
                    "minimumVectorSimilarity must be between -1 and 1");
        }
        Objects.requireNonNull(contextBudget, "contextBudget");
    }

    public LightRagQueryRequest.Options contextOptions(int requestedLimit) {
        int effectiveChunkTopK = Math.min(
                chunkTopK,
                Math.max(requestedLimit, 1));
        return new LightRagQueryRequest.Options(
                com.orgmemory.graphrag.query.LightRagQueryMode.MIX,
                com.orgmemory.graphrag.query.QueryOutputMode.CONTEXT,
                "Multiple Paragraphs",
                "",
                topK,
                effectiveChunkTopK,
                relatedChunkNumber,
                maximumGraphDepth,
                LightRagQueryRequest.RelatedChunkSelection.VECTOR,
                contextBudget,
                false,
                0.0,
                minimumVectorSimilarity,
                includeHeadings,
                false);
    }

    public static GraphRagRetrievalPolicy defaults() {
        return new GraphRagRetrievalPolicy(
                20,
                60,
                20,
                5,
                1,
                0.2,
                true,
                SecureContextBudget.lightRagCompatibleDefaults());
    }
}
