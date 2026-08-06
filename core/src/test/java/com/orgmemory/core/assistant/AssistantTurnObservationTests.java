package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import com.orgmemory.core.assistant.observability.AssistantStageEventSink;
import com.orgmemory.core.assistant.observability.AssistantTurnMeterObservationHandler;
import com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearch;
import com.orgmemory.core.knowledge.search.RetrievedKnowledgeEvidence;
import com.orgmemory.core.knowledge.search.SecureKnowledgeSearchResult;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.Clearance;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;

/**
 * Time to first token is the number a user's "it's slow" complaint is actually about, and it is
 * not a fraction of anything already measured: it contains permission-scoped retrieval, which
 * Spring AI's {@code gen_ai.client.operation} starts after, and it excludes the whole
 * generation that follows the first token, which the turn timer includes.
 *
 * <p>So these fix the two ends that make the measurement mean that. Both use real elapsed time
 * because the measurement uses {@code System.nanoTime}, which no virtual clock moves.
 */
class AssistantTurnObservationTests {

    private static final String CONVERSATION_ID = "31000000-0000-0000-0000-000000000001";
    private static final Duration SLOW_ENOUGH_TO_MEASURE = Duration.ofMillis(150);

    private final CanonicalHybridKnowledgeSearch retrieval =
            mock(CanonicalHybridKnowledgeSearch.class);
    private final ChatModelPort chat = mock(ChatModelPort.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ObservationRegistry observations = observationRegistry();
    private final AssistantStageEventSink stages =
            mock(AssistantStageEventSink.class);
    private final CurrentActor actor = new CurrentActor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Laura",
            "laura@example.test",
            Clearance.STANDARD);

    /**
     * Retrieval runs before the model is asked anything, and the user is already waiting
     * through it. A measurement that started at the model call would report a fast assistant
     * while the person watched a blank screen for the OpenFGA batch check and the ledger
     * re-read.
     */
    @Test
    void countsTheWaitBeforeTheModelIsEvenAsked() {
        whenSearchTakes(SLOW_ENOUGH_TO_MEASURE);
        whenModelStreams(Flux.just("Sixty", " days."));

        drain(startTurn());

        assertTrue(
                timeToFirstToken().totalTime(TimeUnit.NANOSECONDS)
                        >= SLOW_ENOUGH_TO_MEASURE.toNanos(),
                "retrieval happens while the user waits, so it belongs inside the measurement");
    }

    /**
     * The other end. If this recorded the last token instead, it would be the turn duration
     * under a name that promises something else — and the two numbers only diverge on exactly
     * the long answers the metric exists to reassure people about.
     */
    @Test
    void stopsAtTheFirstTokenRatherThanTheLast() {
        whenSearchTakes(Duration.ZERO);
        whenModelStreams(Flux.concat(
                Flux.just("Sixty"),
                Flux.just(" days.").delayElements(SLOW_ENOUGH_TO_MEASURE)));

        drain(startTurn());

        assertTrue(
                timeToFirstToken().totalTime(TimeUnit.NANOSECONDS) < SLOW_ENOUGH_TO_MEASURE.toNanos(),
                "the first token arrived before the delayed one, so the measurement must have"
                        + " stopped there");
    }

    /**
     * A turn that never emits has no first token, and a zero would sit on the same chart
     * looking like the fastest answer the system ever gave.
     */
    @Test
    void recordsNothingWhenNoTokenEverReachedTheCaller() {
        whenSearchTakes(Duration.ZERO);
        when(chat.stream(any(UUID.class), any(AiWorkload.class), any(ChatGenerationRequest.class), anyString()))
                .thenReturn(Flux.error(new AiGatewayUnavailableException("gateway down")));

        assertThrows(AssistantUnavailableException.class, () -> drain(startTurn()));

        assertNull(
                meters.find(AssistantTurnMeterObservationHandler.TIME_TO_FIRST_TOKEN).timer(),
                "a failed turn must not contribute a sample to a latency distribution");
    }

    @Test
    void recordsNothingWhenThereWasNoAccessibleEvidenceToAnswerFrom() {
        when(retrieval.search(any(CurrentActor.class), anyString(), anyInt(), anyString()))
                .thenReturn(new SecureKnowledgeSearchResult("request-1", List.of()));

        drain(startTurn());

        assertNull(
                meters.find(AssistantTurnMeterObservationHandler.TIME_TO_FIRST_TOKEN).timer(),
                "no model was asked, so there is no time-to-first-token to report");
    }

    /**
     * The same rule the GraphRAG meters are held to. An organization or request identifier on a
     * meter grows one series per tenant or per call, and the series never go away.
     */
    @Test
    void carriesNoIdentifierThatWouldGrowASeriesPerTenantOrRequest() {
        whenSearchTakes(Duration.ZERO);
        whenModelStreams(Flux.just("Sixty days."));
        drain(startTurn());

        Set<String> forbidden = Set.of(
                "organization_id", "organizationId", "request_id", "requestId",
                "conversation_id", "conversationId", "actor", "question");

        for (Meter meter : meters.getMeters()) {
            for (Tag tag : meter.getId().getTags()) {
                assertTrue(
                        !forbidden.contains(tag.getKey()),
                        () -> meter.getId().getName() + " carries " + tag.getKey()
                                + ", which grows one series per tenant or request");
            }
        }
        assertTrue(
                meters.getMeters().stream().anyMatch(meter -> meter.getId().getName()
                        .equals(AssistantTurnMeterObservationHandler.TIME_TO_FIRST_TOKEN)),
                "no meter was created, so the sweep above proves nothing");
    }

    @Test
    void tagsTheEngineSoTwoRetrievalImplementationsStaySeparable() {
        whenSearchTakes(Duration.ZERO);
        whenModelStreams(Flux.just("Sixty days."));
        drain(startTurn());

        assertEquals(
                "graph_rag",
                timeToFirstToken().getId().getTag("engine"),
                "comparing engines is the reason the tag exists");
    }

    @Test
    void recordsPermissionScopedRetrievalSeparatelyFromModelLatency() {
        whenSearchTakes(SLOW_ENOUGH_TO_MEASURE);
        whenModelStreams(Flux.just("Sixty days."));

        drain(startTurn());

        Timer retrievalTimer = meters.find(
                        AssistantTurnMeterObservationHandler.RETRIEVAL_DURATION)
                .timer();
        assertTrue(retrievalTimer != null, "the handler recorded no retrieval duration");
        assertTrue(
                retrievalTimer.totalTime(TimeUnit.NANOSECONDS)
                        >= SLOW_ENOUGH_TO_MEASURE.toNanos(),
                "permission-scoped retrieval needs its own latency distribution");
    }

    @Test
    void attributesPromptAssemblyAndRetrievalToFirstTokenAboveRetrieval() {
        whenSearchTakes(Duration.ZERO);
        whenModelStreams(Flux.just("Sixty days."));

        drain(startTurn());

        ArgumentCaptor<AssistantStageEventSink.AssistantStageEvent> captured =
                ArgumentCaptor.forClass(
                        AssistantStageEventSink.AssistantStageEvent.class);
        verify(stages, times(2)).emit(captured.capture());
        assertEquals(
                Set.of(
                        AssistantStageEventSink.Stage.GROUNDING_TO_PROMPT,
                        AssistantStageEventSink.Stage.RETRIEVAL_TO_FIRST_TOKEN),
                captured.getAllValues().stream()
                        .map(AssistantStageEventSink.AssistantStageEvent::stage)
                        .collect(java.util.stream.Collectors.toSet()));
        assertTrue(captured.getAllValues().stream().allMatch(event ->
                !event.duration().isNegative()
                        && event.failureCode() == null));
    }

    private AssistantTurn startTurn() {
        return service().startTurn(actor, "What is the probation policy?", 5, "request-1", CONVERSATION_ID);
    }

    private AssistantService service() {
        return new AssistantService(
                retrieval,
                chat,
                observations,
                AssistantTurnEvent.RetrievalEngine.GRAPH_RAG,
                stages);
    }

    private static void drain(AssistantTurn turn) {
        turn.content().collectList().block(Duration.ofSeconds(10));
    }

    private Timer timeToFirstToken() {
        Timer timer = meters.find(AssistantTurnMeterObservationHandler.TIME_TO_FIRST_TOKEN).timer();
        assertTrue(timer != null, "the handler recorded no time to first token");
        return timer;
    }

    private void whenSearchTakes(Duration delay) {
        when(retrieval.search(any(CurrentActor.class), anyString(), anyInt(), anyString()))
                .thenAnswer(invocation -> {
                    if (!delay.isZero()) {
                        Thread.sleep(delay.toMillis());
                    }
                    return new SecureKnowledgeSearchResult("request-1", List.of(evidence()));
                });
    }

    private void whenModelStreams(Flux<String> tokens) {
        when(chat.stream(any(UUID.class), any(AiWorkload.class), any(ChatGenerationRequest.class), anyString()))
                .thenReturn(tokens);
    }

    private ObservationRegistry observationRegistry() {
        ObservationRegistry registry = ObservationRegistry.create();
        registry.observationConfig()
                .observationHandler(new AssistantTurnMeterObservationHandler(meters));
        return registry;
    }

    private static RetrievedKnowledgeEvidence evidence() {
        return new RetrievedKnowledgeEvidence(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Employee Handbook",
                "The probation period is 60 days.",
                "https://example.test/employee-handbook",
                4,
                4,
                "Probation",
                0.8,
                0.9,
                0.95,
                UUID.randomUUID(),
                UUID.randomUUID(),
                "model-1",
                UUID.randomUUID(),
                1L);
    }
}
