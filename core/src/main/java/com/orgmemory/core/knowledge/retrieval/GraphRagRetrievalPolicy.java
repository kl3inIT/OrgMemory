package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.graphrag.query.LightRagQueryRequest;
import com.orgmemory.graphrag.query.SecureContextBudget;
import java.util.Objects;

/**
 * Deployment policy for the LightRAG application use case. Query semantics
 * stay in graph-rag-core; the Spring runtime owns bounded operational limits.
 */
public record GraphRagRetrievalPolicy(
        int maximumKnowledgeSpaces,
        int maximumConcurrentSpaces,
        int retrievalAdmissionPermits,
        int topK,
        int chunkTopK,
        int relatedChunkNumber,
        int maximumGraphDepth,
        int maximumEvidenceClosure,
        double minimumVectorSimilarity,
        boolean includeHeadings,
        RerankPolicy rerank,
        SecureContextBudget contextBudget) {

    public GraphRagRetrievalPolicy {
        if (maximumKnowledgeSpaces <= 0
                || maximumConcurrentSpaces <= 0
                || maximumConcurrentSpaces > maximumKnowledgeSpaces
                || retrievalAdmissionPermits <= 0
                || topK <= 0
                || chunkTopK <= 0
                || relatedChunkNumber <= 0
                || maximumGraphDepth < 0
                || maximumEvidenceClosure <= 0) {
            throw new IllegalArgumentException(
                    "GraphRAG retrieval limits must be positive and bounded");
        }
        if (!Double.isFinite(minimumVectorSimilarity)
                || minimumVectorSimilarity < -1.0
                || minimumVectorSimilarity > 1.0) {
            throw new IllegalArgumentException(
                    "minimumVectorSimilarity must be between -1 and 1");
        }
        Objects.requireNonNull(rerank, "rerank");
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
                rerank.enabled(),
                rerank.minimumScore(),
                minimumVectorSimilarity,
                includeHeadings,
                false);
    }

    public static GraphRagRetrievalPolicy defaults() {
        return new GraphRagRetrievalPolicy(
                20,
                4,
                4,
                40,
                20,
                5,
                1,
                2_000,
                0.2,
                true,
                RerankPolicy.disabled(),
                SecureContextBudget.lightRagCompatibleDefaults());
    }

    public record RerankPolicy(
            boolean enabled,
            String provider,
            double minimumScore) {

        public RerankPolicy {
            provider = provider == null || provider.isBlank()
                    ? "none"
                    : provider.strip();
            if (!Double.isFinite(minimumScore)) {
                throw new IllegalArgumentException(
                        "minimumScore must be finite");
            }
            if (enabled && provider.equalsIgnoreCase("none")) {
                throw new IllegalArgumentException(
                        "enabled reranking requires a provider");
            }
        }

        public static RerankPolicy disabled() {
            return new RerankPolicy(false, "none", 0.0);
        }
    }
}
