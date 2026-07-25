package com.orgmemory.integrations.graphrag.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.summarization.DescriptionSummaryRequest;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class SpringAiDescriptionSummaryModelTests {

    @Test
    void keepsVisibleDescriptionsInUserDataAndPinsModelOptions() {
        RecordingChatModel chatModel = new RecordingChatModel();
        SpringAiDescriptionSummaryModel model =
                new SpringAiDescriptionSummaryModel("gpt-5.6-sol", chatModel);

        String result = model.summarize(new DescriptionSummaryRequest(
                "Entity",
                "OrgMemory",
                List.of("Public fact one", "Public fact two"),
                Locale.forLanguageTag("vi-VN"),
                256,
                "auth-scope",
                "projection-scope"));

        assertEquals("Grounded summary", result);
        assertFalse(chatModel.prompt.getSystemMessage().getText().contains("Public fact"));
        assertTrue(chatModel.prompt.getUserMessage().getText().contains("Public fact one"));
        assertTrue(chatModel.prompt.getUserMessage().getText().contains("vi-VN"));
        assertEquals("gpt-5.6-sol", chatModel.prompt.getOptions().getModel());
        assertEquals(256, chatModel.prompt.getOptions().getMaxTokens());
    }

    private static final class RecordingChatModel implements ChatModel {

        private Prompt prompt;

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage("Grounded summary"))));
        }
    }
}
