package com.orgmemory.api.assistant;

import com.orgmemory.core.knowledge.GraphRagRetrievalPolicy;
import com.orgmemory.graphrag.query.SecureContextBudget;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.graph-rag.query")
record GraphRagQueryRuntimeProperties(
        String language,
        String tokenizerEncoding,
        Integer maximumEmbeddingBatchSize,
        Integer maximumKnowledgeSpaces,
        Integer maximumConcurrentSpaces,
        Integer topK,
        Integer chunkTopK,
        Integer relatedChunkNumber,
        Integer maximumGraphDepth,
        Integer maximumEvidenceClosure,
        Double minimumVectorSimilarity,
        Boolean includeHeadings,
        Boolean rerankEnabled,
        String rerankProvider,
        Double minimumRerankScore,
        Duration keywordCacheTtl) {

    GraphRagQueryRuntimeProperties {
        language = text(
                language,
                "the same language as the user query");
        tokenizerEncoding = text(tokenizerEncoding, "o200k_base");
        maximumEmbeddingBatchSize = positive(
                maximumEmbeddingBatchSize,
                64,
                "maximumEmbeddingBatchSize");
        maximumKnowledgeSpaces = positive(
                maximumKnowledgeSpaces,
                20,
                "maximumKnowledgeSpaces");
        maximumConcurrentSpaces = positive(
                maximumConcurrentSpaces,
                4,
                "maximumConcurrentSpaces");
        if (maximumConcurrentSpaces > maximumKnowledgeSpaces) {
            throw new IllegalArgumentException(
                    "maximumConcurrentSpaces must not exceed maximumKnowledgeSpaces");
        }
        topK = positive(topK, 60, "topK");
        chunkTopK = positive(chunkTopK, 20, "chunkTopK");
        relatedChunkNumber = positive(
                relatedChunkNumber,
                5,
                "relatedChunkNumber");
        maximumGraphDepth = nonNegative(
                maximumGraphDepth,
                1,
                "maximumGraphDepth");
        maximumEvidenceClosure = positive(
                maximumEvidenceClosure,
                2_000,
                "maximumEvidenceClosure");
        minimumVectorSimilarity =
                minimumVectorSimilarity == null
                        ? 0.2
                        : minimumVectorSimilarity;
        if (!Double.isFinite(minimumVectorSimilarity)
                || minimumVectorSimilarity < -1.0
                || minimumVectorSimilarity > 1.0) {
            throw new IllegalArgumentException(
                    "minimumVectorSimilarity must be between -1 and 1");
        }
        includeHeadings =
                includeHeadings == null || includeHeadings;
        rerankEnabled = rerankEnabled != null && rerankEnabled;
        rerankProvider = text(rerankProvider, "none");
        minimumRerankScore =
                minimumRerankScore == null ? 0.0 : minimumRerankScore;
        if (!Double.isFinite(minimumRerankScore)) {
            throw new IllegalArgumentException(
                    "minimumRerankScore must be finite");
        }
        if (rerankEnabled && rerankProvider.equalsIgnoreCase("none")) {
            throw new IllegalArgumentException(
                    "enabled reranking requires a provider");
        }
        keywordCacheTtl = keywordCacheTtl == null
                ? Duration.ofHours(24)
                : keywordCacheTtl;
        if (keywordCacheTtl.isZero()
                || keywordCacheTtl.isNegative()
                || keywordCacheTtl.compareTo(Duration.ofDays(30)) > 0) {
            throw new IllegalArgumentException(
                    "keywordCacheTtl must be between 1 ns and 30 days");
        }
    }

    GraphRagRetrievalPolicy toPolicy() {
        return new GraphRagRetrievalPolicy(
                maximumKnowledgeSpaces,
                maximumConcurrentSpaces,
                topK,
                chunkTopK,
                relatedChunkNumber,
                maximumGraphDepth,
                maximumEvidenceClosure,
                minimumVectorSimilarity,
                includeHeadings,
                new GraphRagRetrievalPolicy.RerankPolicy(
                        rerankEnabled,
                        rerankProvider,
                        minimumRerankScore),
                SecureContextBudget.lightRagCompatibleDefaults());
    }

    private static int positive(
            Integer value,
            int fallback,
            String field) {
        int resolved = value == null ? fallback : value;
        if (resolved <= 0) {
            throw new IllegalArgumentException(
                    field + " must be positive");
        }
        return resolved;
    }

    private static int nonNegative(
            Integer value,
            int fallback,
            String field) {
        int resolved = value == null ? fallback : value;
        if (resolved < 0) {
            throw new IllegalArgumentException(
                    field + " must be non-negative");
        }
        return resolved;
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.strip();
    }
}
