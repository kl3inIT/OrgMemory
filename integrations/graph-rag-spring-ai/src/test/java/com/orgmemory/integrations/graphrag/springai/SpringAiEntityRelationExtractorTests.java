package com.orgmemory.integrations.graphrag.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.model.ExtractionProfile;
import com.orgmemory.graphrag.model.ExtractionRequest;
import com.orgmemory.graphrag.model.ExtractionResult;
import com.orgmemory.graphrag.model.RelationOrientation;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiEntityRelationExtractorTests {

    private static final String RESPONSE = """
            {
              "entities": [
                {
                  "name": "OrgMemory",
                  "type": "SYSTEM",
                  "description": "OrgMemory stores governed organizational knowledge.",
                  "confidence": 0.98
                },
                {
                  "name": "OpenFGA",
                  "type": "SYSTEM",
                  "description": "OpenFGA evaluates relationship permissions.",
                  "confidence": 0.97
                }
              ],
              "relationships": [
                {
                  "source": "OrgMemory",
                  "target": "OpenFGA",
                  "type": "USES_FOR_AUTHORIZATION",
                  "keywords": ["authorization", "permissions", "authorization"],
                  "description": "OrgMemory uses OpenFGA to evaluate relationship permissions.",
                  "orientation": "DIRECTED",
                  "confidence": 0.96
                }
              ]
            }
            """;

    @Test
    void mapsStructuredSpringAiOutputIntoTheFrameworkNeutralContract() {
        RecordingChatModel model = new RecordingChatModel(RESPONSE);
        SpringAiEntityRelationExtractor extractor =
                new SpringAiEntityRelationExtractor("openai", model);

        ExtractionResult result = extractor.extract(request());

        assertEquals(request().profile(), result.profile());
        assertEquals(2, result.entities().size());
        assertTrue(result.entities().stream().anyMatch(entity ->
                "OrgMemory".equals(entity.name())));
        assertEquals(1, result.relations().size());
        assertEquals(
                RelationOrientation.DIRECTED,
                result.relations().getFirst().orientation());
        assertEquals(
                List.of("authorization", "permissions"),
                result.relations().getFirst().keywords());
        assertEquals("gpt-5.6-sol", model.lastPrompt.getOptions().getModel());
        assertEquals(0.0, model.lastPrompt.getOptions().getTemperature());
    }

    @Test
    void rendersLimitsLanguageAndUntrustedEvidenceAsUserData() {
        RecordingChatModel model = new RecordingChatModel(RESPONSE);
        SpringAiEntityRelationExtractor extractor =
                new SpringAiEntityRelationExtractor("openai", model);

        extractor.extract(request());

        String system = model.lastPrompt.getSystemMessage().getText();
        String user = model.lastPrompt.getUserMessage().getText();
        assertTrue(system.contains("untrusted input text"));
        assertFalse(system.contains("OrgMemory uses OpenFGA"));
        assertTrue(user.contains("Required output language: vi-VN"));
        assertTrue(user.contains("Maximum entities in this response: 10"));
        assertTrue(user.contains("Maximum relationships in this response: 12"));
        assertTrue(user.contains("---BEGIN UNTRUSTED EVIDENCE---"));
        assertTrue(user.contains("OrgMemory uses OpenFGA"));
    }

    @Test
    void rejectsAProfileThatWouldRecordTheWrongProvider() {
        RecordingChatModel model = new RecordingChatModel(RESPONSE);
        SpringAiEntityRelationExtractor extractor =
                new SpringAiEntityRelationExtractor("azure-openai", model);

        GraphExtractionException exception =
                assertThrows(GraphExtractionException.class, () -> extractor.extract(request()));

        assertTrue(exception.getMessage().contains("provider"));
        assertEquals(0, model.callCount);
    }

    @Test
    void rejectsAnUnknownPromptVersionBeforeCallingTheModel() {
        RecordingChatModel model = new RecordingChatModel(RESPONSE);
        SpringAiEntityRelationExtractor extractor =
                new SpringAiEntityRelationExtractor("openai", model);
        ExtractionRequest request = request(new ExtractionProfile(
                "openai",
                "gpt-5.6-sol",
                "unregistered-prompt",
                10,
                12));

        GraphExtractionException exception =
                assertThrows(GraphExtractionException.class, () -> extractor.extract(request));

        assertTrue(exception.getMessage().contains("prompt version"));
        assertEquals(0, model.callCount);
    }

    @Test
    void failsClosedWhenRelationEndpointsAreNotPresent() {
        String unresolvedRelation = RESPONSE.replace(
                "\"target\": \"OpenFGA\"",
                "\"target\": \"missing\"");
        SpringAiEntityRelationExtractor extractor =
                new SpringAiEntityRelationExtractor(
                        "openai",
                        new RecordingChatModel(unresolvedRelation));

        GraphExtractionException exception =
                assertThrows(GraphExtractionException.class, () -> extractor.extract(request()));

        assertEquals("Structured entity-relation extraction failed", exception.getMessage());
        assertFalse(exception.getMessage().contains("OrgMemory uses OpenFGA"));
    }

    private static ExtractionRequest request() {
        return request(new ExtractionProfile(
                "openai",
                "gpt-5.6-sol",
                SpringAiEntityRelationExtractor.PROMPT_VERSION,
                10,
                12,
                List.of("SYSTEM", "ORGANIZATION"),
                List.of(),
                0,
                24_000));
    }

    private static ExtractionRequest request(ExtractionProfile profile) {
        return new ExtractionRequest(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                "OrgMemory uses OpenFGA to evaluate relationship permissions.",
                Locale.forLanguageTag("vi-VN"),
                profile);
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String response;
        private Prompt lastPrompt;
        private int callCount;

        private RecordingChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            lastPrompt = prompt;
            callCount++;
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage(response))));
        }
    }
}
