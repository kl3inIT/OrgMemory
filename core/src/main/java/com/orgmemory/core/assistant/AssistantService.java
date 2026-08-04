package com.orgmemory.core.assistant;

import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.ai.AssistantModelRouteAuthority;
import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
import com.orgmemory.core.assistant.observability.AssistantStageEventSink.AssistantStageEvent;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import com.orgmemory.core.assistant.observability.AssistantTurnObservationContext;
import com.orgmemory.core.assistant.observability.AssistantTurnObservationDocumentation;
import com.orgmemory.core.assistant.observability.DefaultAssistantTurnObservationConvention;
import com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch;
import com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence;
import com.orgmemory.core.organization.CurrentActor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Flux;

public class AssistantService {

    static final String NO_ACCESSIBLE_EVIDENCE =
            "I could not find enough accessible company knowledge to answer that question.";

    private static final DefaultAssistantTurnObservationConvention DEFAULT_CONVENTION =
            new DefaultAssistantTurnObservationConvention();

    private final PermissionAwareKnowledgeSearch retrieval;
    private final ChatModelPort chat;
    private final ObservationRegistry observations;
    private final AssistantTurnEvent.RetrievalEngine engine;
    private final AssistantStageEventSink stages;

    /**
     * @param engine which retrieval implementation was wired in. Passed rather than asked of
     *     {@code retrieval}, because {@code PermissionAwareKnowledgeSearch} is engine-neutral
     *     on purpose and adding a self-describing method to it for telemetry's benefit would
     *     invert the dependency the interface exists to prevent. The wiring already knows.
     */
    public AssistantService(
            PermissionAwareKnowledgeSearch retrieval,
            ChatModelPort chat,
            ObservationRegistry observations,
            AssistantTurnEvent.RetrievalEngine engine) {
        this(
                retrieval,
                chat,
                observations,
                engine,
                AssistantStageEventSink.NO_OP);
    }

    public AssistantService(
            PermissionAwareKnowledgeSearch retrieval,
            ChatModelPort chat,
            ObservationRegistry observations,
            AssistantTurnEvent.RetrievalEngine engine,
            AssistantStageEventSink stages) {
        this.retrieval = retrieval;
        this.chat = chat;
        this.observations = observations;
        this.engine = engine;
        this.stages = stages;
    }

    /**
     * Runs one assistant turn and observes it end to end.
     *
     * <p>The observation outlives this method. Retrieval and prompt assembly happen here, the
     * tokens arrive later on whichever thread the reactive chain lands on, and the turn is not
     * over until the stream is. So the observation is started here and stopped from the
     * stream's terminal signal, and no thread-local scope is opened across that boundary —
     * a scope closed on a different thread than it opened is how context propagation breaks
     * quietly.
     */
    public AssistantTurn startTurn(
            CurrentActor actor,
            String question,
            Integer requestedLimit,
            String requestId,
            String conversationId) {
        return startTurn(
                actor,
                question,
                requestedLimit,
                requestId,
                conversationId,
                null);
    }

    public AssistantTurn startTurn(
            CurrentActor actor,
            String question,
            Integer requestedLimit,
            String requestId,
            String conversationId,
            AssistantModelRouteAuthority routeAuthority) {
        AssistantTurnObservationContext context = new AssistantTurnObservationContext(
                actor.organizationId(), engine, System.nanoTime());
        Observation observation = AssistantTurnObservationDocumentation.TURN
                .observation(null, DEFAULT_CONVENTION, () -> context, observations)
                .start();

        try {
            var search = retrieval.search(actor, question, requestedLimit, requestId);
            if (search.evidence().isEmpty()) {
                context.foundNoEvidence(System.nanoTime());
                observation.stop();
                return new AssistantTurn(search.requestId(), List.of(), Flux.just(NO_ACCESSIBLE_EVIDENCE));
            }

            long retrievalCompletedAt = System.nanoTime();
            PreparedTurn prepared;
            try {
                prepared = search.grounding()
                        .map(grounding -> new PreparedTurn(
                                AssistantPromptFactory.addUserContext(
                                        grounding.generationRequest(),
                                        actor),
                                numbered(grounding.citations())))
                        .orElseGet(() -> {
                            AssistantPromptFactory.PreparedPrompt prompt =
                                    AssistantPromptFactory.create(
                                            question,
                                            search.evidence(),
                                            actor);
                            return new PreparedTurn(
                                    prompt.request(),
                                    prompt.citations());
                        });
                emitStage(
                        AssistantStageEventSink.Stage.GROUNDING_TO_PROMPT,
                        AssistantStageEventSink.Outcome.SUCCEEDED,
                        retrievalCompletedAt,
                        null);
            } catch (RuntimeException failure) {
                emitStage(
                        AssistantStageEventSink.Stage.GROUNDING_TO_PROMPT,
                        AssistantStageEventSink.Outcome.FAILED,
                        retrievalCompletedAt,
                        "prompt_assembly_failed");
                throw failure;
            }

            int evidenceCount = search.evidence().size();
            int citationCount = prepared.citations().size();
            // A returned Flux is cold and could be subscribed more than once; Micrometer's
            // stop is not idempotent, so the second terminal signal must not reach it.
            java.util.concurrent.atomic.AtomicBoolean stopped =
                    new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.atomic.AtomicBoolean firstTokenStageEmitted =
                    new java.util.concurrent.atomic.AtomicBoolean();
            Flux<String> generated = routeAuthority == null
                    ? chat.stream(
                            actor.organizationId(),
                            AiWorkload.ASSISTANT_CHAT,
                            prepared.request(),
                            conversationId)
                    : chat.stream(
                            routeAuthority,
                            prepared.request(),
                            conversationId,
                            actor.userId(),
                            requestId);
            Flux<String> content = generated
                    .filter(token -> token != null && !token.isEmpty())
                    .switchIfEmpty(Flux.error(new AssistantUnavailableException(
                            "The assistant returned no answer")))
                    .onErrorMap(
                            error -> !(error instanceof AssistantUnavailableException),
                            error -> new AssistantUnavailableException("The assistant is unavailable", error))
                    // Before the error mapping's terminal signal, so the moment the caller
                    // could first see something is recorded even on a stream that later fails.
                    .doOnNext(token -> {
                        long firstTokenAt = System.nanoTime();
                        context.firstTokenAt(firstTokenAt);
                        if (firstTokenStageEmitted.compareAndSet(
                                false,
                                true)) {
                            emitStage(
                                    AssistantStageEventSink.Stage
                                            .RETRIEVAL_TO_FIRST_TOKEN,
                                    AssistantStageEventSink.Outcome.SUCCEEDED,
                                    retrievalCompletedAt,
                                    null);
                        }
                    })
                    .doOnError(error -> {
                        context.unavailable(System.nanoTime(), "assistant_stream_failed");
                        observation.error(error);
                    })
                    .doOnComplete(() ->
                            context.answered(System.nanoTime(), evidenceCount, citationCount))
                    .doOnCancel(() ->
                            context.unavailable(System.nanoTime(), "assistant_stream_cancelled"))
                    .doFinally(signal -> {
                        if (stopped.compareAndSet(false, true)) {
                            observation.stop();
                        }
                    });

            return new AssistantTurn(
                    search.requestId(),
                    prepared.citations(),
                    content);
        } catch (RuntimeException exception) {
            throw failed(observation, context, exception);
        }
    }

    private void emitStage(
            AssistantStageEventSink.Stage stage,
            AssistantStageEventSink.Outcome outcome,
            long startedAt,
            String failureCode) {
        stages.emit(new AssistantStageEvent(
                engine,
                stage,
                outcome,
                Duration.ofNanos(Math.max(
                        0L,
                        System.nanoTime() - startedAt)),
                failureCode,
                Instant.now()));
    }

    /**
     * Ends the observation for a turn that failed before it could return a stream, so a
     * failure raised during retrieval is not left as an observation nothing ever stops.
     */
    private static AssistantUnavailableException failed(
            Observation observation,
            AssistantTurnObservationContext context,
            RuntimeException exception) {
        AssistantUnavailableException failure =
                exception instanceof AssistantUnavailableException unavailable
                        ? unavailable
                        : new AssistantUnavailableException("The assistant is unavailable", exception);
        context.unavailable(System.nanoTime(), "assistant_turn_failed");
        observation.error(failure);
        observation.stop();
        return failure;
    }

    private static List<AssistantCitation> numbered(
            List<RetrievedKnowledgeEvidence> evidence) {
        ArrayList<AssistantCitation> citations =
                new ArrayList<>(evidence.size());
        for (int index = 0; index < evidence.size(); index++) {
            citations.add(new AssistantCitation(index + 1, evidence.get(index)));
        }
        return List.copyOf(citations);
    }

    private record PreparedTurn(
            ChatGenerationRequest request,
            List<AssistantCitation> citations) {

        private PreparedTurn {
            citations = List.copyOf(citations);
        }
    }
}
