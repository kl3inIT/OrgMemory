package com.orgmemory.graphrag.observability;

import static com.orgmemory.graphrag.validation.TextValidation.normalizeOptional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Provider-neutral telemetry boundary. Events intentionally contain no query,
 * prompt, completion or evidence text.
 */
@FunctionalInterface
public interface GraphRagEventSink {

    Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    Pattern FAILURE_CODE = Pattern.compile("[a-z0-9_]{1,64}");

    GraphRagEventSink NO_OP = event -> { };

    void emit(GraphRagEvent event);

    /**
     * Fans one event out to every sink so an application can observe the same
     * stage through more than one backend.
     *
     * <p>Sinks fail independently: one failing backend still lets the others
     * receive the event. The first failure is rethrown with the remaining ones
     * suppressed, so a caller that already treats emission as non-critical keeps
     * that behavior and a caller that does not still learns something broke.
     */
    static GraphRagEventSink composite(List<GraphRagEventSink> sinks) {
        List<GraphRagEventSink> delegates =
                List.copyOf(Objects.requireNonNull(sinks, "sinks"));
        if (delegates.isEmpty()) {
            return NO_OP;
        }
        if (delegates.size() == 1) {
            return delegates.getFirst();
        }
        return event -> {
            RuntimeException failure = null;
            for (GraphRagEventSink delegate : delegates) {
                try {
                    delegate.emit(event);
                } catch (RuntimeException sinkFailure) {
                    if (failure == null) {
                        failure = sinkFailure;
                    } else if (failure != sinkFailure) {
                        // Throwable.addSuppressed rejects self-suppression, and two
                        // sinks can raise one shared instance.
                        failure.addSuppressed(sinkFailure);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        };
    }

    /**
     * Wraps a sink so an emission failure is absorbed, counted and reported
     * instead of being silently discarded at every call site.
     *
     * @see FailureTolerantGraphRagEventSink
     */
    static FailureTolerantGraphRagEventSink failureTolerant(GraphRagEventSink sink) {
        return new FailureTolerantGraphRagEventSink(sink);
    }

    record GraphRagEvent(
            UUID operationId,
            UUID organizationId,
            Stage stage,
            Outcome outcome,
            Duration duration,
            int inputCount,
            int outputCount,
            String modelRouteFingerprint,
            String scopeFingerprint,
            CacheStatus cacheStatus,
            String failureCode,
            TokenUsage tokenUsage,
            ProviderTokenUsage providerTokens,
            Instant occurredAt) {

        /**
         * Describes a stage that measures no tokens at all, which is most of them.
         * The overload exists so that adding measurements the few token-bearing
         * stages take does not oblige the rest to pass a row of {@code null}s a
         * reader would have to count commas through.
         */
        public GraphRagEvent(
                UUID operationId,
                UUID organizationId,
                Stage stage,
                Outcome outcome,
                Duration duration,
                int inputCount,
                int outputCount,
                String modelRouteFingerprint,
                String scopeFingerprint,
                CacheStatus cacheStatus,
                String failureCode,
                Instant occurredAt) {
            this(
                    operationId,
                    organizationId,
                    stage,
                    outcome,
                    duration,
                    inputCount,
                    outputCount,
                    modelRouteFingerprint,
                    scopeFingerprint,
                    cacheStatus,
                    failureCode,
                    null,
                    null,
                    occurredAt);
        }

        public GraphRagEvent {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(duration, "duration");
            if (duration.isNegative()) {
                throw new IllegalArgumentException("duration must not be negative");
            }
            if (inputCount < 0 || outputCount < 0) {
                throw new IllegalArgumentException("counts must be non-negative");
            }
            modelRouteFingerprint = normalizeOptional(modelRouteFingerprint);
            scopeFingerprint = normalizeOptional(scopeFingerprint);
            failureCode = normalizeOptional(failureCode);
            if (modelRouteFingerprint != null
                    && !SHA_256.matcher(modelRouteFingerprint).matches()) {
                throw new IllegalArgumentException(
                        "modelRouteFingerprint must be a lowercase SHA-256 value");
            }
            if (scopeFingerprint != null
                    && !SHA_256.matcher(scopeFingerprint).matches()) {
                throw new IllegalArgumentException(
                        "scopeFingerprint must be a lowercase SHA-256 value");
            }
            if (outcome == Outcome.FAILED && failureCode == null) {
                throw new IllegalArgumentException(
                        "failureCode is required for a failed event");
            }
            if (failureCode != null
                    && !FAILURE_CODE.matcher(failureCode).matches()) {
                throw new IllegalArgumentException(
                        "failureCode must be a bounded machine code");
            }
            Objects.requireNonNull(occurredAt, "occurredAt");
        }
    }

    /**
     * Token cost of one assembled generation context.
     *
     * <p>Counts, not content. A token count cannot reconstruct the text it
     * measures, which is why this record is allowed through a boundary that
     * refuses queries, prompts and evidence.
     *
     * <p>Deliberately a projection of the query model's own token record rather
     * than a reference to it. That record exists to enforce a retrieval budget
     * and is free to change shape for retrieval reasons; a telemetry contract
     * that moved with it would rewrite dashboards for a decision that had
     * nothing to do with them. The two overlap today and may diverge.
     *
     * @param promptTokens         what the model is actually charged for, which
     *                             exceeds the sum of the channels below by the
     *                             cost of rendering them into one prompt
     * @param budgetTokens         the ceiling {@code promptTokens} was fitted to,
     *                             so a collector can express headroom as a ratio
     *                             without knowing this deployment's configuration
     * @param droppedContributions entities, relations and chunks evicted to make
     *                             the context fit. This is the input-side
     *                             truncation signal: zero means the retrieved
     *                             context reached the model whole, and a rising
     *                             count means answers are degrading for a reason
     *                             no latency or error metric reports.
     */
    record TokenUsage(
            int promptTokens,
            int systemPromptTokens,
            int queryTokens,
            int entityTokens,
            int relationTokens,
            int chunkTokens,
            int budgetTokens,
            int droppedContributions) {

        public TokenUsage {
            if (promptTokens < 0
                    || systemPromptTokens < 0
                    || queryTokens < 0
                    || entityTokens < 0
                    || relationTokens < 0
                    || chunkTokens < 0
                    || droppedContributions < 0) {
                throw new IllegalArgumentException(
                        "token counts must be non-negative");
            }
            if (budgetTokens <= 0) {
                throw new IllegalArgumentException(
                        "budgetTokens must be positive");
            }
        }

        public boolean truncated() {
            return droppedContributions > 0;
        }
    }

    /**
     * What a model provider reported for one stage's calls.
     *
     * <p>Separate from {@link TokenUsage} because the two answer different
     * questions with the same unit. {@code TokenUsage} is a budget: what
     * retrieval assembled, against the ceiling it had to fit. This is a bill:
     * what a provider counted and will charge for. Folding them together would
     * make a dashboard add a number the deployment estimated to a number a vendor
     * invoiced, and the difference between those two is itself worth seeing.
     *
     * <p>Counts only, so it crosses the same boundary for the same reason: a
     * total cannot reconstruct the prompt it totalled.
     */
    record ProviderTokenUsage(int inputTokens, int outputTokens) {

        public ProviderTokenUsage {
            if (inputTokens < 0 || outputTokens < 0) {
                throw new IllegalArgumentException(
                        "provider token counts must be non-negative");
            }
        }

        public int totalTokens() {
            return Math.addExact(inputTokens, outputTokens);
        }
    }

    /**
     * Every stage this engine runs, and only those. {@code GENERATE} was declared here and
     * never produced: {@code GraphRagRetrievalPolicy} pins {@code CONTEXT} so generation
     * happens in the application shell, above an engine-neutral interface, where labelling it
     * a GraphRAG stage would be false whenever the canonical engine is the one running. It was
     * removed on 2026-07-30 rather than left as a value nothing can emit, which is the same
     * defect class as an exporter that reports healthy while pushing to nowhere. The assistant
     * turn is observed on its own payload-free surface; see decision 0020.
     */
    enum Stage {
        PARSE,
        CHUNK,
        EXTRACT,
        GLEAN,
        MERGE,
        EMBED,
        PUBLISH,
        AUTHORIZE,
        PREPARE_QUERY,
        RETRIEVE_SNAPSHOT,
        RETRIEVE,
        RERANK,
        ASSEMBLE_CONTEXT
    }

    enum Outcome {
        SUCCEEDED,
        FAILED,
        CANCELLED
    }

    enum CacheStatus {
        HIT,
        MISS,
        BYPASS
    }

}
