package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record LightRagQueryResult(
        Status status,
        String context,
        String prompt,
        Answer answer,
        List<Reference> references,
        Trace trace) {

    public LightRagQueryResult {
        Objects.requireNonNull(status, "status");
        context = Objects.requireNonNull(context, "context");
        prompt = Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(answer, "answer");
        references = List.copyOf(Objects.requireNonNull(references, "references"));
        Objects.requireNonNull(trace, "trace");
        if (status == Status.NO_RESULTS && !references.isEmpty()) {
            throw new IllegalArgumentException("no-result queries cannot expose references");
        }
    }

    public enum Status {
        SUCCESS,
        NO_RESULTS
    }

    public sealed interface Answer permits NoAnswer, CompleteAnswer, StreamingAnswer {
    }

    public record NoAnswer() implements Answer {
    }

    public record CompleteAnswer(String content) implements Answer {
        public CompleteAnswer {
            content = requireText(content, "content");
        }
    }

    public record StreamingAnswer(Iterator<String> chunks) implements Answer {
        public StreamingAnswer {
            Objects.requireNonNull(chunks, "chunks");
        }
    }

    public record Reference(
            int id,
            EvidenceReference evidence,
            String sourceLabel,
            Map<String, String> metadata) {

        public Reference {
            if (id <= 0) {
                throw new IllegalArgumentException("reference id must be positive");
            }
            Objects.requireNonNull(evidence, "evidence");
            sourceLabel = requireText(sourceLabel, "sourceLabel");
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
        }
    }

    public record Trace(
            LightRagQueryMode mode,
            KeywordPlan keywords,
            List<String> embeddingInputs,
            int entitySeedCount,
            int relationSeedCount,
            int vectorChunkCount,
            int selectedEntityCount,
            int selectedRelationCount,
            int selectedChunkCount,
            boolean rerankAttempted,
            boolean rerankFallback,
            List<ChunkSignal> chunkSignals,
            String authorizationFingerprint,
            long projectionGeneration,
            String failureReason) {

        public Trace {
            Objects.requireNonNull(mode, "mode");
            Objects.requireNonNull(keywords, "keywords");
            embeddingInputs = List.copyOf(Objects.requireNonNull(embeddingInputs, "embeddingInputs"));
            if (entitySeedCount < 0
                    || relationSeedCount < 0
                    || vectorChunkCount < 0
                    || selectedEntityCount < 0
                    || selectedRelationCount < 0
                    || selectedChunkCount < 0
                    || projectionGeneration <= 0) {
                throw new IllegalArgumentException("trace counts and generation must be valid");
            }
            chunkSignals = List.copyOf(Objects.requireNonNull(chunkSignals, "chunkSignals"));
            authorizationFingerprint =
                    requireText(authorizationFingerprint, "authorizationFingerprint");
            failureReason = failureReason == null ? "" : failureReason.strip();
        }
    }

    public record ChunkSignal(
            UUID chunkId,
            Origin origin,
            int frequency,
            int order,
            double retrievalScore,
            Double rerankScore) {

        public ChunkSignal {
            Objects.requireNonNull(chunkId, "chunkId");
            Objects.requireNonNull(origin, "origin");
            if (frequency <= 0 || order <= 0 || !Double.isFinite(retrievalScore)) {
                throw new IllegalArgumentException("chunk signal values must be valid");
            }
            if (rerankScore != null && !Double.isFinite(rerankScore)) {
                throw new IllegalArgumentException("rerankScore must be finite");
            }
        }
    }

    public enum Origin {
        ENTITY,
        RELATION,
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
