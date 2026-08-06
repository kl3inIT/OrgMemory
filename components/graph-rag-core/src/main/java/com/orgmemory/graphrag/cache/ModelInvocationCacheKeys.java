package com.orgmemory.graphrag.cache;

import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Exact key factories for the model-cache surfaces in LightRAG v1.5.4. */
public final class ModelInvocationCacheKeys {

    public static final String QUERY_EMBEDDING_OPERATION = "QUERY_EMBEDDING";

    private ModelInvocationCacheKeys() {
    }

    public static ModelInvocationCache.Key extraction(
            ProjectionNamespace namespace,
            String canonicalPrompt,
            String continuationHistory,
            String modelRouteFingerprint,
            String profileFingerprint) {
        return key(
                namespace,
                "EXTRACTION",
                Map.of(
                        "prompt", Objects.requireNonNull(canonicalPrompt, "canonicalPrompt"),
                        "continuationHistory",
                                Objects.requireNonNull(
                                        continuationHistory, "continuationHistory")),
                modelRouteFingerprint,
                profileFingerprint);
    }

    public static ModelInvocationCache.Key keywords(
            ProjectionNamespace namespace,
            String normalizedQuery,
            String language,
            String strategy,
            String modelRouteFingerprint,
            String profileFingerprint) {
        return key(
                namespace,
                "KEYWORDS",
                Map.of(
                        "query", requireText(normalizedQuery, "normalizedQuery"),
                        "language", requireText(language, "language"),
                        "strategy", requireText(strategy, "strategy")),
                modelRouteFingerprint,
                profileFingerprint);
    }

    public static ModelInvocationCache.Key queryEmbedding(
            ProjectionNamespace namespace,
            String exactInput,
            String embeddingComponent,
            java.util.UUID embeddingProfileId,
            int dimensions) {
        if (dimensions <= 0) {
            throw new IllegalArgumentException("dimensions must be positive");
        }
        return key(
                namespace,
                QUERY_EMBEDDING_OPERATION,
                Map.of(
                        "input", requireExactInput(exactInput),
                        "component", requireText(embeddingComponent, "embeddingComponent"),
                        "embeddingProfileId",
                                Objects.requireNonNull(
                                                embeddingProfileId, "embeddingProfileId")
                                        .toString(),
                        "dimensions", Integer.toString(dimensions),
                        "normalization", "unicode-nfc-v1"),
                embeddingComponent,
                embeddingProfileId + ":" + dimensions + ":unicode-nfc-v1");
    }

    public static ModelInvocationCache.Key permissionScopedSummary(
            ProjectionNamespace namespace,
            String canonicalVisibleDescriptions,
            String authorizationFingerprint,
            String modelRouteFingerprint,
            String profileFingerprint) {
        return key(
                namespace,
                "SUMMARY",
                Map.of(
                        "visibleDescriptions",
                                Objects.requireNonNull(
                                        canonicalVisibleDescriptions,
                                        "canonicalVisibleDescriptions"),
                        "authorizationFingerprint",
                                requireText(
                                        authorizationFingerprint,
                                        "authorizationFingerprint")),
                modelRouteFingerprint,
                profileFingerprint);
    }

    private static ModelInvocationCache.Key key(
            ProjectionNamespace namespace,
            String operation,
            Map<String, String> semanticInput,
            String modelRouteFingerprint,
            String profileFingerprint) {
        Objects.requireNonNull(namespace, "namespace");
        Map<String, String> fields = new LinkedHashMap<>(semanticInput);
        fields.put("organizationId", namespace.organizationId().toString());
        fields.put("workspace", namespace.workspace());
        fields.put("collection", namespace.collection());
        return new ModelInvocationCache.Key(
                namespace,
                operation,
                CanonicalCacheKeyHasher.sha256(
                        "orgmemory.graph-rag.model-invocation.v1", fields),
                modelRouteFingerprint,
                profileFingerprint);
    }

    private static String requireExactInput(String value) {
        String exact = Objects.requireNonNull(value, "exactInput");
        if (exact.isBlank()) {
            throw new IllegalArgumentException("exactInput must not be blank");
        }
        return exact;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
