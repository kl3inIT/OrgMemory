package com.orgmemory.integrations.graphrag.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.multimodal.MultimodalAnalysisCacheKey;
import com.orgmemory.graphrag.multimodal.MultimodalAnalysisContent;
import com.orgmemory.graphrag.multimodal.MultimodalAnalysisOutcome;
import com.orgmemory.graphrag.multimodal.MultimodalAnalysisRequest;
import com.orgmemory.graphrag.multimodal.MultimodalAnalysisRoute;
import com.orgmemory.graphrag.multimodal.MultimodalAnalyzerRole;
import com.orgmemory.graphrag.multimodal.MultimodalBinaryArtifact;
import com.orgmemory.graphrag.multimodal.MultimodalEvidenceScope;
import com.orgmemory.graphrag.multimodal.MultimodalModality;
import com.orgmemory.graphrag.multimodal.MultimodalPayload;
import com.orgmemory.graphrag.multimodal.MultimodalPromptFactory;
import com.orgmemory.graphrag.multimodal.MultimodalSidecarItem;
import com.orgmemory.graphrag.multimodal.MultimodalSurroundingContext;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;

class SpringAiMultimodalAnalyzerTests {

    @Test
    void attachesOpaqueImageEvidenceAndRetriesInvalidJsonOnce() {
        RecordingChatModel model = new RecordingChatModel(
                "not-json",
                """
                {
                  "name": "Employee onboarding flow",
                  "imageType": "Diagram",
                  "description": "A flow from offer acceptance to first-day setup."
                }
                """);
        SpringAiMultimodalAnalyzer analyzer = new SpringAiMultimodalAnalyzer(
                "openai",
                MultimodalAnalyzerRole.VISION,
                model,
                artifact -> new ByteArrayResource(new byte[] {1, 2, 3}));

        MultimodalAnalysisOutcome outcome = analyzer.analyze(imageRequest());

        MultimodalAnalysisOutcome.Success success = assertInstanceOf(
                MultimodalAnalysisOutcome.Success.class, outcome);
        MultimodalAnalysisContent.Image content = assertInstanceOf(
                MultimodalAnalysisContent.Image.class, success.content());
        assertEquals("Employee onboarding flow", content.name());
        assertEquals(2, model.calls);
        assertEquals(1, model.lastPrompt.getUserMessage().getMedia().size());
        assertEquals(
                "image/png",
                model.lastPrompt
                        .getUserMessage()
                        .getMedia()
                        .getFirst()
                        .getMimeType()
                        .toString());
        assertEquals("gpt-5.6-sol", model.lastPrompt.getOptions().getModel());
        assertTrue(model.lastPrompt
                .getUserMessage()
                .getText()
                .contains("previous response was invalid"));
        assertTrue(model.lastPrompt
                .getSystemMessage()
                .getText()
                .contains("untrusted enterprise document"));
    }

    @Test
    void providerFailureIsNotReclassifiedAsASkip() {
        ChatModel failing = prompt -> {
            throw new IllegalStateException("provider unavailable");
        };
        SpringAiMultimodalAnalyzer analyzer = new SpringAiMultimodalAnalyzer(
                "openai",
                MultimodalAnalyzerRole.VISION,
                failing,
                artifact -> new ByteArrayResource(new byte[] {1}));

        MultimodalAnalysisOutcome.Failure failure = assertInstanceOf(
                MultimodalAnalysisOutcome.Failure.class,
                analyzer.analyze(imageRequest()));

        assertEquals("MODEL_INVOCATION_FAILED", failure.reasonCode());
        assertTrue(failure.transientFailure());
    }

    private static MultimodalAnalysisRequest imageRequest() {
        MultimodalBinaryArtifact artifact = new MultimodalBinaryArtifact(
                "opaque-artifact-1",
                "image/png",
                3,
                "a".repeat(64),
                OptionalInt.of(1),
                OptionalInt.of(1));
        MultimodalSidecarItem item = new MultimodalSidecarItem(
                "image-1",
                MultimodalModality.IMAGE,
                0,
                7,
                34,
                List.of("Employee onboarding"),
                "New hire setup",
                "",
                new MultimodalPayload.Image(artifact),
                Map.of());
        MultimodalEvidenceScope evidence = new MultimodalEvidenceScope(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                4);
        MultimodalAnalysisRoute route = new MultimodalAnalysisRoute(
                "openai",
                "gpt-5.6-sol",
                "2026-07-01",
                MultimodalPromptFactory.VERSION,
                MultimodalAnalyzerRole.VISION,
                0.0,
                400);
        return new MultimodalAnalysisRequest(
                evidence,
                item,
                new MultimodalSurroundingContext(
                        item.headingPath(),
                        "Before",
                        "After",
                        item.caption(),
                        item.footnotes(),
                        "orgmemory-context/v1"),
                route,
                new MultimodalAnalysisCacheKey("b".repeat(64)));
    }

    private static final class RecordingChatModel implements ChatModel {

        private final Queue<String> responses;
        private Prompt lastPrompt;
        private int calls;

        private RecordingChatModel(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt = prompt;
            calls++;
            return new ChatResponse(List.of(new Generation(
                    new AssistantMessage(responses.remove()))));
        }
    }
}
