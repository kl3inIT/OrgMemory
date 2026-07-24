package com.orgmemory.integrations.graphrag.springai;

import static com.orgmemory.graphrag.validation.TextValidation.requireText;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.query.QueryAnswerModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/** Spring AI effect adapter for complete and streaming grounded answers. */
public final class SpringAiQueryAnswerModel implements QueryAnswerModel {

    private final String modelId;
    private final ChatModel chatModel;

    public SpringAiQueryAnswerModel(String modelId, ChatModel chatModel) {
        this.modelId = requireText(modelId, "modelId");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    }

    @Override
    public ProcessingComponentRef component() {
        return new ProcessingComponentRef("spring-ai-query-answer", "1");
    }

    @Override
    public Response answer(Request request) {
        Objects.requireNonNull(request, "request");
        Prompt prompt = prompt(request);
        if (!request.streaming()) {
            return new Complete(SpringAiKeywordPlanningModel.responseText(
                    chatModel.call(prompt),
                    "Query answer model"));
        }
        var chunks = chatModel.stream(prompt)
                .map(response -> {
                    if (response == null
                            || response.getResult() == null
                            || response.getResult().getOutput() == null) {
                        return "";
                    }
                    String text = response.getResult().getOutput().getText();
                    return text == null ? "" : text;
                })
                .filter(text -> !text.isEmpty())
                .toIterable()
                .iterator();
        return new Streaming(chunks);
    }

    private Prompt prompt(Request request) {
        List<org.springframework.ai.chat.messages.Message> messages =
                new ArrayList<>(request.conversationHistory().size() + 2);
        messages.add(new SystemMessage(request.systemPrompt()));
        request.conversationHistory().stream()
                .map(SpringAiQueryAnswerModel::message)
                .forEach(messages::add);
        messages.add(new UserMessage(request.query()));
        return new Prompt(
                messages,
                ChatOptions.builder()
                        .model(modelId)
                        .build());
    }

    private static org.springframework.ai.chat.messages.Message message(
            QueryAnswerModel.Message message) {
        return switch (message.role()) {
            case USER -> new UserMessage(message.content());
            case ASSISTANT -> new AssistantMessage(message.content());
        };
    }
}
