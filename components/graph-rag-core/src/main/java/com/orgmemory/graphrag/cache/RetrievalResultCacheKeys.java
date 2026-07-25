package com.orgmemory.graphrag.cache;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical exact-key factory for completed retrieval/query results. */
public final class RetrievalResultCacheKeys {

    private RetrievalResultCacheKeys() {
    }

    public static RetrievalResultCache.Key query(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            QuerySemantics semantics,
            String modelRouteFingerprint) {
        Objects.requireNonNull(semantics, "semantics");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("query", semantics.normalizedQuery());
        fields.put("responseType", semantics.responseType());
        fields.put("topK", Integer.toString(semantics.topK()));
        fields.put("chunkTopK", Integer.toString(semantics.chunkTopK()));
        fields.put("entityTokenBudget", Integer.toString(semantics.entityTokenBudget()));
        fields.put("relationTokenBudget", Integer.toString(semantics.relationTokenBudget()));
        fields.put("chunkTokenBudget", Integer.toString(semantics.chunkTokenBudget()));
        fields.put("highKeywords", semantics.canonicalHighKeywords());
        fields.put("lowKeywords", semantics.canonicalLowKeywords());
        fields.put("userInstruction", semantics.userInstruction());
        fields.put("reranker", semantics.rerankerFingerprint());
        fields.put("includeHeadings", Boolean.toString(semantics.includeHeadings()));
        fields.put("streaming", Boolean.toString(semantics.streaming()));
        String queryHash =
                CanonicalCacheKeyHasher.sha256("orgmemory.graph-rag.query.v1", fields);
        return RetrievalResultCache.key(
                scope,
                snapshot,
                queryHash,
                semantics.strategy(),
                modelRouteFingerprint);
    }

    public record QuerySemantics(
            String normalizedQuery,
            String strategy,
            String responseType,
            int topK,
            int chunkTopK,
            int entityTokenBudget,
            int relationTokenBudget,
            int chunkTokenBudget,
            String canonicalHighKeywords,
            String canonicalLowKeywords,
            String userInstruction,
            String rerankerFingerprint,
            boolean includeHeadings,
            boolean streaming) {

        public QuerySemantics {
            normalizedQuery = requireText(normalizedQuery, "normalizedQuery");
            strategy = requireText(strategy, "strategy");
            responseType = requireText(responseType, "responseType");
            canonicalHighKeywords =
                    Objects.requireNonNull(canonicalHighKeywords, "canonicalHighKeywords");
            canonicalLowKeywords =
                    Objects.requireNonNull(canonicalLowKeywords, "canonicalLowKeywords");
            userInstruction = Objects.requireNonNull(userInstruction, "userInstruction");
            rerankerFingerprint =
                    requireText(rerankerFingerprint, "rerankerFingerprint");
            if (topK <= 0 || chunkTopK <= 0) {
                throw new IllegalArgumentException("topK and chunkTopK must be positive");
            }
            if (entityTokenBudget < 0
                    || relationTokenBudget < 0
                    || chunkTokenBudget < 0) {
                throw new IllegalArgumentException("token budgets must be non-negative");
            }
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
