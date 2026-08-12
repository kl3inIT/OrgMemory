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
import com.orgmemory.core.knowledge.search.KnowledgeEvidenceSelection;
import com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence;
import com.orgmemory.core.organization.CurrentActor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

public class AssistantService {

    private static final Logger LOG = LoggerFactory.getLogger(AssistantService.class);

    private static final String NO_ACCESSIBLE_EVIDENCE_ENGLISH =
            "I could not find this information in the documents you can access. "
                    + "If you believe this information exists, contact the document owner or an administrator.";
    private static final String NO_ACCESSIBLE_EVIDENCE_VIETNAMESE =
            "Tôi không tìm thấy nội dung này trong các tài liệu bạn truy cập được. "
                    + "Nếu bạn cho rằng thông tin này tồn tại, hãy liên hệ bộ phận sở hữu tài liệu hoặc quản trị viên.";

    private static final String VIETNAMESE_CHARACTERS =
            "ăâđêôơưáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵ"
                    + "ĂÂĐÊÔƠƯÁÀẢÃẠẤẦẨẪẬẮẰẲẴẶÉÈẺẼẸẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌỐỒỔỖỘỚỜỞỠỢÚÙỦŨỤỨỪỬỮỰÝỲỶỸỴ";

    private static final DefaultAssistantTurnObservationConvention DEFAULT_CONVENTION =
            new DefaultAssistantTurnObservationConvention();

    private final PermissionAwareKnowledgeSearch retrieval;
    private final AssistantPrivateFileSearch privateFileRetrieval;
    private final ChatModelPort chat;
    private final AssistantAgentModelPort agent;
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
                null,
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
        this(retrieval, chat, null, observations, engine, stages);
    }

    public AssistantService(
            PermissionAwareKnowledgeSearch retrieval,
            ChatModelPort chat,
            AssistantAgentModelPort agent,
            ObservationRegistry observations,
            AssistantTurnEvent.RetrievalEngine engine,
            AssistantStageEventSink stages) {
        this(retrieval, null, chat, agent, observations, engine, stages);
    }

    public AssistantService(
            PermissionAwareKnowledgeSearch retrieval,
            AssistantPrivateFileSearch privateFileRetrieval,
            ChatModelPort chat,
            AssistantAgentModelPort agent,
            ObservationRegistry observations,
            AssistantTurnEvent.RetrievalEngine engine,
            AssistantStageEventSink stages) {
        this.retrieval = retrieval;
        this.privateFileRetrieval = privateFileRetrieval;
        this.chat = chat;
        this.agent = agent;
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
        return startTurn(
                actor,
                question,
                requestedLimit,
                requestId,
                conversationId,
                routeAuthority,
                System.nanoTime());
    }

    public AssistantTurn startTurn(
            CurrentActor actor,
            String question,
            Integer requestedLimit,
            String requestId,
            String conversationId,
            AssistantModelRouteAuthority routeAuthority,
            long startedAtNanos) {
        return startTurn(
                actor,
                question,
                requestedLimit,
                requestId,
                conversationId,
                routeAuthority,
                startedAtNanos,
                KnowledgeEvidenceSelection.unrestricted());
    }

    public AssistantTurn startTurn(
            CurrentActor actor,
            String question,
            Integer requestedLimit,
            String requestId,
            String conversationId,
            AssistantModelRouteAuthority routeAuthority,
            long startedAtNanos,
            KnowledgeEvidenceSelection evidenceSelection) {
        AssistantTurnObservationContext context = new AssistantTurnObservationContext(
                actor.organizationId(), engine, startedAtNanos);
        Observation observation = AssistantTurnObservationDocumentation.TURN
                .observation(null, DEFAULT_CONVENTION, () -> context, observations)
                .start();

        try {
            KnowledgeEvidenceSelection selection = evidenceSelection == null
                    ? KnowledgeEvidenceSelection.unrestricted()
                    : evidenceSelection;
            var search = selection.restricted()
                    ? retrieval.search(
                            actor,
                            question,
                            requestedLimit,
                            requestId,
                            selection)
                    : retrieval.search(
                            actor,
                            question,
                            requestedLimit,
                            requestId);
            context.retrievalCompletedAt(System.nanoTime());
            if (!selection.missingSourceObjectIds(search.evidence()).isEmpty()) {
                throw new AssistantUnavailableException(
                        "Selected file evidence is unavailable",
                        null,
                        "assistant_evidence_unavailable");
            }
            if (search.evidence().isEmpty()) {
                context.foundNoEvidence(System.nanoTime());
                observation.stop();
                return new AssistantTurn(
                        search.requestId(),
                        List.of(),
                        Flux.just(noAccessibleEvidence(question)));
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
            return generate(
                    actor, requestId, conversationId, routeAuthority,
                    search.requestId(), prepared, evidenceCount,
                    retrievalCompletedAt, context, observation);
        } catch (RuntimeException exception) {
            throw failed(observation, context, exception);
        }
    }

    public AssistantTurn startTurnWithPrivateFiles(
            CurrentActor actor,
            String question,
            Integer requestedLimit,
            String requestId,
            String conversationId,
            AssistantModelRouteAuthority routeAuthority,
            long startedAtNanos,
            AssistantPrivateFileSelection selection) {
        AssistantTurnObservationContext context = new AssistantTurnObservationContext(
                actor.organizationId(), engine, startedAtNanos);
        Observation observation = AssistantTurnObservationDocumentation.TURN
                .observation(null, DEFAULT_CONVENTION, () -> context, observations)
                .start();
        try {
            if (privateFileRetrieval == null) {
                throw new AssistantUnavailableException(
                        "Private file retrieval is unavailable",
                        null,
                        "assistant_private_file_unavailable");
            }
            AssistantPrivateFileSearchResult search = privateFileRetrieval.search(
                    actor, question, requestedLimit, requestId, selection);
            context.retrievalCompletedAt(System.nanoTime());
            if (search.evidence().isEmpty()) {
                context.foundNoEvidence(System.nanoTime());
                observation.stop();
                return new AssistantTurn(
                        search.requestId(), List.of(), Flux.just(noAccessibleEvidence(question)));
            }
            long retrievalCompletedAt = System.nanoTime();
            PreparedTurn prepared;
            try {
                AssistantPromptFactory.PreparedPrompt prompt = AssistantPromptFactory.createPrivate(
                        question, search.evidence(), actor);
                prepared = new PreparedTurn(prompt.request(), prompt.citations());
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
            return generate(
                    actor, requestId, conversationId, routeAuthority,
                    search.requestId(), prepared, search.evidence().size(),
                    retrievalCompletedAt, context, observation);
        } catch (RuntimeException exception) {
            throw failed(observation, context, exception);
        }
    }

    private AssistantTurn generate(
            CurrentActor actor,
            String requestId,
            String conversationId,
            AssistantModelRouteAuthority routeAuthority,
            String resolvedRequestId,
            PreparedTurn prepared,
            int evidenceCount,
            long retrievalCompletedAt,
            AssistantTurnObservationContext context,
            Observation observation) {
        int citationCount = prepared.citations().size();
        java.util.concurrent.atomic.AtomicBoolean stopped = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean firstTokenStageEmitted = new java.util.concurrent.atomic.AtomicBoolean();
        Sinks.Many<AssistantAgentActivity> activitySink = Sinks.many().replay().limit(32);
        Flux<String> generated = routeAuthority == null || agent == null
                ? chat.stream(actor.organizationId(), AiWorkload.ASSISTANT_CHAT, prepared.request(), conversationId)
                : agent.stream(
                        routeAuthority, prepared.request(), conversationId, actor, requestId,
                        activity -> activitySink.tryEmitNext(activity));
        Flux<String> content = generated
                .filter(token -> token != null && !token.isEmpty())
                .switchIfEmpty(Flux.error(new AssistantUnavailableException("The assistant returned no answer")))
                .onErrorMap(
                        error -> !(error instanceof AssistantUnavailableException),
                        error -> new AssistantUnavailableException("The assistant is unavailable", error))
                .doOnNext(token -> {
                    long firstTokenAt = System.nanoTime();
                    context.firstTokenAt(firstTokenAt);
                    if (firstTokenStageEmitted.compareAndSet(false, true)) {
                        emitStage(
                                AssistantStageEventSink.Stage.RETRIEVAL_TO_FIRST_TOKEN,
                                AssistantStageEventSink.Outcome.SUCCEEDED,
                                retrievalCompletedAt,
                                null);
                    }
                })
                .doOnError(error -> {
                    context.unavailable(System.nanoTime(), "assistant_stream_failed");
                    observation.error(error);
                    LOG.warn("assistant turn unavailable failure_code={}", "assistant_stream_failed", error);
                })
                .doOnComplete(() -> context.answered(System.nanoTime(), evidenceCount, citationCount))
                .doOnCancel(() -> context.unavailable(System.nanoTime(), "assistant_stream_cancelled"))
                .doFinally(signal -> {
                    activitySink.tryEmitComplete();
                    if (stopped.compareAndSet(false, true)) observation.stop();
                });
        return new AssistantTurn(resolvedRequestId, prepared.citations(), content, activitySink.asFlux());
    }

    private static String noAccessibleEvidence(String question) {
        if (question != null && looksVietnamese(question)) {
            return NO_ACCESSIBLE_EVIDENCE_VIETNAMESE;
        }
        return NO_ACCESSIBLE_EVIDENCE_ENGLISH;
    }

    private static boolean looksVietnamese(String question) {
        if (question.chars().anyMatch(character -> VIETNAMESE_CHARACTERS.indexOf(character) >= 0)) {
            return true;
        }
        String normalized = question.strip().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("cho toi")
                || normalized.contains("la gi")
                || normalized.contains("bao nhieu")
                || normalized.contains("the nao")
                || normalized.contains("co duoc")
                || normalized.startsWith("hay ");
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
        String failureCode = failure.failureCode() == null
                ? "assistant_turn_failed"
                : failure.failureCode();
        context.unavailable(System.nanoTime(), failureCode);
        observation.error(failure);
        observation.stop();
        // An unavailable turn is a BusinessException, so the API layer answers it without
        // logging. That left the only failures users actually report with no diagnostic at
        // all. The cause carries the stack; the message is already caller-safe.
        LOG.warn("assistant turn unavailable failure_code={}", failureCode, failure);
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
