package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.graphrag.query.QueryAnswerModel;
import com.orgmemory.integrations.ai.gateway.SpringAiChatModelProvider;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

class OrganizationAwareQueryAnswerModelTests {

    @Test
    void resolvesTheAnswerRouteForEveryOrganizationRequest() {
        UUID firstOrganization = UUID.randomUUID();
        UUID secondOrganization = UUID.randomUUID();
        AiRoute firstRoute = new AiRoute("first", "answer-a");
        AiRoute secondRoute = new AiRoute("second", "answer-b");
        AiRouteResolver routes = mock(AiRouteResolver.class);
        SpringAiChatModelProvider models = mock(SpringAiChatModelProvider.class);
        when(routes.resolve(firstOrganization, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(firstRoute);
        when(routes.resolve(secondOrganization, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(secondRoute);
        when(models.resolve(
                        firstOrganization,
                        AiWorkload.ASSISTANT_CHAT,
                        firstRoute))
                .thenReturn(new RecordingChatModel("answer-a"));
        when(models.resolve(
                        secondOrganization,
                        AiWorkload.ASSISTANT_CHAT,
                        secondRoute))
                .thenReturn(new RecordingChatModel("answer-b"));
        OrganizationAwareQueryAnswerModel model =
                new OrganizationAwareQueryAnswerModel(routes, models);

        assertEquals(
                "answer-a",
                complete(model.answer(request(firstOrganization))).content());
        assertEquals(
                "answer-b",
                complete(model.answer(request(secondOrganization))).content());
    }

    private static QueryAnswerModel.Request request(UUID organizationId) {
        return new QueryAnswerModel.Request(
                organizationId,
                "What is the leave policy?",
                "Use only governed evidence.",
                List.of(),
                false);
    }

    private static QueryAnswerModel.Complete complete(
            QueryAnswerModel.Response response) {
        return (QueryAnswerModel.Complete) response;
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String response;

        private RecordingChatModel(String response) {
            this.response = response;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(
                    new Generation(new AssistantMessage(response))));
        }

        @Override
        public ChatOptions getOptions() {
            return ChatOptions.builder().build();
        }
    }
}
