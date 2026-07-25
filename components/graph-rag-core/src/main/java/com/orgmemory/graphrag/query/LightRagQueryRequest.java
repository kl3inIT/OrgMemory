package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record LightRagQueryRequest(
        AuthorizedEvidenceScope scope,
        ProjectionSnapshot snapshot,
        String query,
        Options options,
        UUID embeddingProfileId,
        int embeddingDimensions,
        KeywordPlan trustedKeywords,
        List<QueryAnswerModel.Message> conversationHistory) {

    public LightRagQueryRequest {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!scope.organizationId().equals(snapshot.namespace().organizationId())) {
            throw new IllegalArgumentException(
                    "authorization scope and snapshot belong to different organizations");
        }
        query = requireText(query, "query");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(embeddingProfileId, "embeddingProfileId");
        if (embeddingDimensions <= 0) {
            throw new IllegalArgumentException("embeddingDimensions must be positive");
        }
        conversationHistory =
                List.copyOf(Objects.requireNonNull(conversationHistory, "conversationHistory"));
        if (!conversationHistory.isEmpty() && options.outputMode() != QueryOutputMode.ANSWER) {
            throw new IllegalArgumentException(
                    "conversation history is only valid for answer generation");
        }
        if (trustedKeywords != null && !options.mode().usesGraph()) {
            throw new IllegalArgumentException(
                    "trusted keywords are only valid for graph query modes");
        }
    }

    public record Options(
            LightRagQueryMode mode,
            QueryOutputMode outputMode,
            String responseType,
            String userInstruction,
            int topK,
            int chunkTopK,
            int relatedChunkNumber,
            int maximumGraphDepth,
            RelatedChunkSelection relatedChunkSelection,
            SecureContextBudget contextBudget,
            boolean rerankEnabled,
            double minimumRerankScore,
            double minimumVectorSimilarity,
            boolean includeHeadings,
            boolean streaming) {

        public Options {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(outputMode, "outputMode");
            responseType = requireText(responseType, "responseType");
            userInstruction = Objects.requireNonNull(userInstruction, "userInstruction").strip();
            if (topK <= 0 || chunkTopK <= 0 || relatedChunkNumber <= 0) {
                throw new IllegalArgumentException(
                        "topK, chunkTopK and relatedChunkNumber must be positive");
            }
            if (maximumGraphDepth < 0) {
                throw new IllegalArgumentException("maximumGraphDepth must be non-negative");
            }
            Objects.requireNonNull(relatedChunkSelection, "relatedChunkSelection");
            Objects.requireNonNull(contextBudget, "contextBudget");
            if (!Double.isFinite(minimumRerankScore)) {
                throw new IllegalArgumentException("minimumRerankScore must be finite");
            }
            if (!Double.isFinite(minimumVectorSimilarity)
                    || minimumVectorSimilarity < -1.0
                    || minimumVectorSimilarity > 1.0) {
                throw new IllegalArgumentException(
                        "minimumVectorSimilarity must be between -1 and 1");
            }
            if (mode == LightRagQueryMode.BYPASS && outputMode != QueryOutputMode.ANSWER) {
                throw new IllegalArgumentException(
                        "bypass mode only supports direct answer generation");
            }
            if (streaming && outputMode != QueryOutputMode.ANSWER) {
                throw new IllegalArgumentException(
                        "streaming is only valid for answer generation");
            }
        }

        public static Options mixDefaults() {
            return new Options(
                    LightRagQueryMode.MIX,
                    QueryOutputMode.ANSWER,
                    "Multiple Paragraphs",
                    "",
                    60,
                    20,
                    5,
                    1,
                    RelatedChunkSelection.VECTOR,
                    SecureContextBudget.lightRagCompatibleDefaults(),
                    true,
                    0.5,
                    0.2,
                    true,
                    true);
        }
    }

    public enum RelatedChunkSelection {
        WEIGHT,
        VECTOR
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
