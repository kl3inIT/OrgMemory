package com.orgmemory.integrations.graphrag.springai;

import static com.orgmemory.graphrag.validation.TextValidation.requireText;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.query.KeywordPlan;
import com.orgmemory.graphrag.query.KeywordPlanningModel;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;

/** Spring AI effect adapter for the core-owned LightRAG keyword prompt. */
public final class SpringAiKeywordPlanningModel implements KeywordPlanningModel {

    private final String modelId;
    private final ChatModel chatModel;
    private final BeanOutputConverter<StructuredKeywordPlanResponse> converter =
            new BeanOutputConverter<>(StructuredKeywordPlanResponse.class);

    public SpringAiKeywordPlanningModel(String modelId, ChatModel chatModel) {
        this.modelId = requireText(modelId, "modelId");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    }

    @Override
    public ProcessingComponentRef component() {
        return new ProcessingComponentRef("spring-ai-keyword-planner", "1");
    }

    @Override
    public KeywordPlan complete(String prompt) {
        String corePrompt = requireText(prompt, "prompt");
        String structuredPrompt = corePrompt
                + "\n\n---Required JSON Schema---\n"
                + converter.getFormat();
        ChatResponse response = chatModel.call(new Prompt(
                List.of(new SystemMessage(structuredPrompt)),
                chatModel.getOptions().mutate()
                        .model(modelId)
                        .temperature(0.0)
                        .build()));
        String content = responseText(response, "Keyword model");
        try {
            StructuredKeywordPlanResponse parsed = converter.convert(content);
            if (parsed == null) {
                throw new GraphQueryModelException(
                        "Keyword model returned no structured response");
            }
            return new KeywordPlan(
                    safe(parsed.highLevelKeywords()),
                    safe(parsed.lowLevelKeywords()),
                    KeywordPlan.Source.MODEL);
        } catch (GraphQueryModelException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GraphQueryModelException(
                    "Keyword model returned malformed structured output",
                    exception);
        }
    }

    private static List<String> safe(List<String> values) {
        return values == null ? List.of() : values;
    }

    static String responseText(ChatResponse response, String role) {
        if (response == null
                || response.getResult() == null
                || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null
                || response.getResult().getOutput().getText().isBlank()) {
            throw new GraphQueryModelException(role + " returned no assistant response");
        }
        return response.getResult().getOutput().getText().strip();
    }
}
