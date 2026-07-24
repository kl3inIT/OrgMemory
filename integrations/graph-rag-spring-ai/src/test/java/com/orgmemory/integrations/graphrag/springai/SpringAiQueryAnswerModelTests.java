package com.orgmemory.integrations.graphrag.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.orgmemory.graphrag.query.QueryAnswerModel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class SpringAiQueryAnswerModelTests {

    @Test
    void sendsSystemHistoryAndCurrentQueryInOrder() {
        RecordingChatModel chatModel = new RecordingChatModel();
        SpringAiQueryAnswerModel model =
                new SpringAiQueryAnswerModel("gpt-5.6-sol", chatModel);

        QueryAnswerModel.Response response = model.answer(new QueryAnswerModel.Request(
                "current question",
                "authorized context",
                List.of(
                        new QueryAnswerModel.Message(
                                QueryAnswerModel.Role.USER, "previous question"),
                        new QueryAnswerModel.Message(
                                QueryAnswerModel.Role.ASSISTANT, "previous answer")),
                false));

        QueryAnswerModel.Complete complete =
                assertInstanceOf(QueryAnswerModel.Complete.class, response);
        assertEquals("grounded answer", complete.content());
        assertEquals(
                List.of(
                        MessageType.SYSTEM,
                        MessageType.USER,
                        MessageType.ASSISTANT,
                        MessageType.USER),
                chatModel.prompt.getInstructions().stream()
                        .map(message -> message.getMessageType())
                        .toList());
        assertEquals("current question", chatModel.prompt.getUserMessage().getText());
        assertEquals("gpt-5.6-sol", chatModel.prompt.getOptions().getModel());
    }

    @Test
    void exposesProviderStreamingWithoutBufferingTheCompleteAnswer() {
        RecordingChatModel chatModel = new RecordingChatModel();
        SpringAiQueryAnswerModel model =
                new SpringAiQueryAnswerModel("gpt-5.6-sol", chatModel);

        QueryAnswerModel.Streaming streaming =
                assertInstanceOf(
                        QueryAnswerModel.Streaming.class,
                        model.answer(new QueryAnswerModel.Request(
                                "question",
                                "authorized context",
                                List.of(),
                                true)));

        assertEquals(List.of("first ", "second"), values(streaming.chunks()));
        assertEquals(0, chatModel.callCount);
        assertEquals(1, chatModel.streamCount);
    }

    private static List<String> values(Iterator<String> iterator) {
        List<String> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private static final class RecordingChatModel implements ChatModel {

        private Prompt prompt;
        private int callCount;
        private int streamCount;

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            callCount++;
            return response("grounded answer");
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            this.prompt = prompt;
            streamCount++;
            return Flux.just(response("first "), response(""), response("second"));
        }

        private static ChatResponse response(String content) {
            return new ChatResponse(
                    List.of(new Generation(new AssistantMessage(content))));
        }
    }
}
