package com.orgmemory.integrations.graphrag.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.query.KeywordPlan;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiKeywordPlanningModelTests {

    @Test
    void pinsTheModelAndParsesTheLightRagKeywordSchema() {
        RecordingChatModel chatModel = new RecordingChatModel("""
                {
                  "high_level_keywords": ["workplace policy"],
                  "low_level_keywords": ["probation", "60 days"]
                }
                """);
        SpringAiKeywordPlanningModel model =
                new SpringAiKeywordPlanningModel("gpt-5.6-sol", chatModel);

        KeywordPlan result = model.complete("core-owned prompt");

        assertEquals(List.of("workplace policy"), result.highLevel());
        assertEquals(List.of("probation", "60 days"), result.lowLevel());
        assertEquals(KeywordPlan.Source.MODEL, result.source());
        assertEquals("gpt-5.6-sol", chatModel.prompt.getOptions().getModel());
        assertEquals(0.0, chatModel.prompt.getOptions().getTemperature());
        assertEquals(0.35, chatModel.prompt.getOptions().getTopP());
        assertTrue(chatModel.prompt.getSystemMessage().getText().contains("core-owned prompt"));
        assertTrue(chatModel.prompt.getSystemMessage().getText().contains("high_level_keywords"));
    }

    @Test
    void rejectsMalformedProviderOutputAtTheAdapterBoundary() {
        SpringAiKeywordPlanningModel model = new SpringAiKeywordPlanningModel(
                "gpt-5.6-sol",
                new RecordingChatModel("{not-json"));

        assertThrows(
                GraphQueryModelException.class,
                () -> model.complete("core-owned prompt"));
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String response;
        private final ChatOptions options =
                ChatOptions.builder().topP(0.35).build();
        private Prompt prompt;

        private RecordingChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage(response))));
        }

        @Override
        public ChatOptions getOptions() {
            return options;
        }
    }
}
