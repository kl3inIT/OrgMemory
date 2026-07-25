package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assistant.AssistantCitation;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.assistant.AssistantTurn;
import com.orgmemory.core.knowledge.RetrievedKnowledgeEvidence;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import tools.jackson.databind.ObjectMapper;

class AssistantControllerStreamingTests {

    @Test
    void streamsTheVerifiedRequestSnapshotWithoutWaitingForModelCompletion() {
        RetrievedKnowledgeEvidence evidence = evidence();
        Sinks.Many<String> model =
                Sinks.many().unicast().onBackpressureBuffer();
        AssistantTurn turn = new AssistantTurn(
                "request-1",
                List.of(new AssistantCitation(1, evidence)),
                model.asFlux());

        StepVerifier.create(controller().parts(turn))
                .assertNext(part -> assertInstanceOf(
                        AssistantStreamPart.StartStep.class,
                        part))
                .assertNext(part -> {
                    var source = assertInstanceOf(
                            AssistantStreamPart.SourceUrl.class,
                            part);
                    assertEquals(
                            "/api/citations/"
                                    + evidence.chunkId()
                                    + "/content",
                            source.url());
                    assertEquals(1, source.citationNumber());
                })
                .then(() -> model.tryEmitNext("answer"))
                .assertNext(part -> assertInstanceOf(
                        AssistantStreamPart.TextStart.class,
                        part))
                .assertNext(part -> {
                    var delta = assertInstanceOf(
                            AssistantStreamPart.TextDelta.class,
                            part);
                    assertEquals("answer", delta.delta());
                })
                .then(model::tryEmitComplete)
                .assertNext(part -> assertInstanceOf(
                        AssistantStreamPart.TextEnd.class,
                        part))
                .assertNext(part -> assertInstanceOf(
                        AssistantStreamPart.FinishStep.class,
                        part))
                .verifyComplete();
    }

    private static AssistantController controller() {
        return new AssistantController(
                mock(AssistantService.class),
                mock(CurrentActorProvider.class),
                mock(AssistantProperties.class),
                mock(ObjectMapper.class));
    }

    private static RetrievedKnowledgeEvidence evidence() {
        return new RetrievedKnowledgeEvidence(
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000003"),
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000004"),
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000005"),
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000006"),
                "Policy",
                "approved evidence",
                null,
                null,
                null,
                null,
                0.0,
                1.0,
                1.0,
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000007"),
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000007"),
                "model-v1",
                UUID.fromString(
                        "43000000-0000-0000-0000-000000000008"),
                1L);
    }
}
