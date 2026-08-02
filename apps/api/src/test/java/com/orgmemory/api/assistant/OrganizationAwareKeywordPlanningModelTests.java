package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.OpenAiReasoningEffort;
import com.orgmemory.graphrag.query.KeywordPlanningModel;
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

class OrganizationAwareKeywordPlanningModelTests {

    @Test
    void resolvesEachOrganizationAndRouteChangeInsideOneRunningProcess() {
        UUID firstOrganization = UUID.randomUUID();
        UUID secondOrganization = UUID.randomUUID();
        AiRouteResolver routes = mock(AiRouteResolver.class);
        SpringAiChatModelProvider models = mock(SpringAiChatModelProvider.class);
        AiRoute firstRoute = new AiRoute(
                "first-gateway",
                "gpt-5.6-luna",
                OpenAiReasoningEffort.NONE);
        AiRoute changedRoute = new AiRoute(
                "first-gateway",
                "gpt-5.6-sol",
                OpenAiReasoningEffort.LOW);
        AiRoute secondRoute = new AiRoute("second-gateway", "gpt-5.4-nano");
        RecordingChatModel firstModel = new RecordingChatModel("first");
        RecordingChatModel changedModel = new RecordingChatModel("changed");
        RecordingChatModel secondModel = new RecordingChatModel("second");
        when(routes.resolve(firstOrganization, AiWorkload.KEYWORD_PLANNING))
                .thenReturn(firstRoute, changedRoute);
        when(routes.resolve(secondOrganization, AiWorkload.KEYWORD_PLANNING))
                .thenReturn(secondRoute);
        when(models.resolve(
                        firstOrganization,
                        AiWorkload.KEYWORD_PLANNING,
                        firstRoute))
                .thenReturn(firstModel);
        when(models.resolve(
                        firstOrganization,
                        AiWorkload.KEYWORD_PLANNING,
                        changedRoute))
                .thenReturn(changedModel);
        when(models.resolve(
                        secondOrganization,
                        AiWorkload.KEYWORD_PLANNING,
                        secondRoute))
                .thenReturn(secondModel);
        OrganizationAwareKeywordPlanningModel model =
                new OrganizationAwareKeywordPlanningModel(routes, models);

        KeywordPlanningModel.Invocation first = model.resolve(firstOrganization);
        KeywordPlanningModel.Invocation second = model.resolve(secondOrganization);
        KeywordPlanningModel.Invocation changed = model.resolve(firstOrganization);

        assertEquals(List.of("first"), first.complete("prompt").lowLevel());
        assertEquals(List.of("second"), second.complete("prompt").lowLevel());
        assertEquals(List.of("changed"), changed.complete("prompt").lowLevel());
        assertNotEquals(first.modelRouteFingerprint(), second.modelRouteFingerprint());
        assertNotEquals(first.modelRouteFingerprint(), changed.modelRouteFingerprint());
        verify(models).resolve(
                firstOrganization,
                AiWorkload.KEYWORD_PLANNING,
                firstRoute);
        verify(models).resolve(
                firstOrganization,
                AiWorkload.KEYWORD_PLANNING,
                changedRoute);
    }

    private static final class RecordingChatModel implements ChatModel {

        private final String keyword;

        private RecordingChatModel(String keyword) {
            this.keyword = keyword;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(
                    "{\"high_level_keywords\":[],\"low_level_keywords\":[\""
                            + keyword
                            + "\"]}"))));
        }

        @Override
        public ChatOptions getOptions() {
            return ChatOptions.builder().build();
        }
    }
}
