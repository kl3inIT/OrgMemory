package com.orgmemory.graphrag.query;

import static com.orgmemory.graphrag.validation.TextValidation.normalizeOptional;

import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable provider output that can be reused across permission-equivalent
 * snapshot reads for one logical LightRAG query.
 *
 * <p>The plan contains no authorization decision or retrieved evidence. Every
 * execution still requires its own authorized scope and pinned snapshot.
 */
public record LightRagPreparedQuery(
        String query,
        LightRagQueryRequest.Options options,
        UUID embeddingProfileId,
        int embeddingDimensions,
        KeywordPlan keywords,
        List<String> embeddingInputs,
        FloatVector queryEmbedding,
        FloatVector lowLevelEmbedding,
        FloatVector highLevelEmbedding,
        List<QueryAnswerModel.Message> conversationHistory,
        Duration keywordPlanningDuration,
        Duration embeddingDuration,
        GraphRagEventSink.CacheStatus keywordCacheStatus,
        String keywordModelRouteFingerprint) {

    public LightRagPreparedQuery {
        query = requireText(query, "query");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(embeddingProfileId, "embeddingProfileId");
        if (embeddingDimensions <= 0) {
            throw new IllegalArgumentException(
                    "embeddingDimensions must be positive");
        }
        Objects.requireNonNull(keywords, "keywords");
        embeddingInputs =
                List.copyOf(Objects.requireNonNull(embeddingInputs, "embeddingInputs"));
        conversationHistory = List.copyOf(
                Objects.requireNonNull(conversationHistory, "conversationHistory"));
        Objects.requireNonNull(
                keywordPlanningDuration,
                "keywordPlanningDuration");
        Objects.requireNonNull(embeddingDuration, "embeddingDuration");
        if (keywordPlanningDuration.isNegative()
                || embeddingDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "preparation durations must not be negative");
        }
        Objects.requireNonNull(keywordCacheStatus, "keywordCacheStatus");
        keywordModelRouteFingerprint =
                normalizeOptional(keywordModelRouteFingerprint);
        requireDimensions(queryEmbedding, embeddingDimensions, "queryEmbedding");
        requireDimensions(
                lowLevelEmbedding,
                embeddingDimensions,
                "lowLevelEmbedding");
        requireDimensions(
                highLevelEmbedding,
                embeddingDimensions,
                "highLevelEmbedding");
    }

    void requireMatches(LightRagQueryRequest request) {
        Objects.requireNonNull(request, "request");
        if (!query.equals(request.query())
                || !options.equals(request.options())
                || !embeddingProfileId.equals(request.embeddingProfileId())
                || embeddingDimensions != request.embeddingDimensions()
                || !conversationHistory.equals(request.conversationHistory())) {
            throw new IllegalArgumentException(
                    "prepared query does not match the execution request");
        }
        KeywordPlan expectedTrusted = request.trustedKeywords() == null
                ? null
                : new KeywordPlan(
                        request.trustedKeywords().highLevel(),
                        request.trustedKeywords().lowLevel(),
                        KeywordPlan.Source.TRUSTED_CALLER);
        boolean trustedMismatch = (expectedTrusted == null
                        && keywords.source()
                                == KeywordPlan.Source.TRUSTED_CALLER)
                || (expectedTrusted != null
                        && !keywords.equals(expectedTrusted));
        if (trustedMismatch) {
            throw new IllegalArgumentException(
                    "prepared query does not match trusted keywords");
        }
    }

    private static void requireDimensions(
            FloatVector vector,
            int dimensions,
            String field) {
        if (vector != null && vector.dimensions() != dimensions) {
            throw new IllegalArgumentException(
                    field + " does not match embeddingDimensions");
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
