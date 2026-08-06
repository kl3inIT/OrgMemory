package com.orgmemory.integrations.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.skill.SkillRuntimeOperations;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.Clearance;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import reactor.core.publisher.Flux;

class AssistantSkillToolLoopTests {

    @Test
    void springAiRecursivelyExecutesARequestLocalSkillToolBeforeAnswering() {
        UUID assetId = UUID.fromString("93000000-0000-0000-0000-000000000001");
        UUID releaseId = UUID.fromString("93000000-0000-0000-0000-000000000002");
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Incident lead",
                "incident.lead@example.test",
                Clearance.STANDARD);
        SkillRuntimeOperations skills = mock(SkillRuntimeOperations.class);
        when(skills.search(actor, "incident", 3)).thenReturn(List.of(
                new SkillRuntimeOperations.SkillSummary(
                        assetId,
                        releaseId,
                        "support/incident-response",
                        "1.0.0",
                        "Incident response",
                        "Coordinate incidents safely",
                        "a".repeat(64))));
        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel model = new ChatModel() {
            @Override
            public ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException("call path is not used");
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                if (modelCalls.getAndIncrement() == 0) {
                    AssistantMessage toolCall = AssistantMessage.builder()
                            .content("")
                            .toolCalls(List.of(new AssistantMessage.ToolCall(
                                    "call-1",
                                    "function",
                                    "search_skills",
                                    "{\"query\":\"incident\",\"limit\":3}")))
                            .build();
                    return Flux.just(new ChatResponse(List.of(new Generation(toolCall))));
                }
                return Flux.just(new ChatResponse(List.of(new Generation(
                        new AssistantMessage("Use the governed incident workflow.")))));
            }
        };
        var callbacks = new AssistantSkillToolCallbacks(skills)
                .create(actor, ignored -> { });

        List<String> answer = ChatClient.builder(model)
                .build()
                .prompt()
                .system("Use tools when relevant.")
                .user("Help with this incident")
                .tools(callbacks)
                .advisors(SpringAiChatModelAdapter.boundedToolCallingAdvisor())
                .stream()
                .content()
                .collectList()
                .block();

        assertEquals(List.of("Use the governed incident workflow."), answer);
        assertEquals(2, modelCalls.get());
        verify(skills).search(actor, "incident", 3);
    }

    @Test
    void stopsAProviderThatKeepsRequestingTools() {
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                "Loop tester",
                "loop.tester@example.test",
                Clearance.STANDARD);
        SkillRuntimeOperations skills = mock(SkillRuntimeOperations.class);
        when(skills.search(actor, "loop", 1)).thenReturn(List.of());
        AtomicInteger modelCalls = new AtomicInteger();
        ChatModel loopingModel = new ChatModel() {
            @Override
            public ChatOptions getOptions() {
                return ToolCallingChatOptions.builder().build();
            }

            @Override
            public ChatResponse call(Prompt prompt) {
                throw new UnsupportedOperationException("call path is not used");
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                int call = modelCalls.incrementAndGet();
                return Flux.just(new ChatResponse(List.of(new Generation(
                        AssistantMessage.builder()
                                .content("")
                                .toolCalls(List.of(new AssistantMessage.ToolCall(
                                        "call-" + call,
                                        "function",
                                        "search_skills",
                                        "{\"query\":\"loop\",\"limit\":1}")))
                                .build()))));
            }
        };

        List<String> answer = ChatClient.builder(loopingModel)
                .build()
                .prompt()
                .user("Keep looping")
                .tools(new AssistantSkillToolCallbacks(skills)
                        .create(actor, ignored -> { }))
                .advisors(SpringAiChatModelAdapter.boundedToolCallingAdvisor())
                .stream()
                .content()
                .collectList()
                .block();

        assertEquals(List.of(), answer);
        assertTrue(modelCalls.get() > 1 && modelCalls.get() <= 9);
        verify(skills, atLeastOnce()).search(actor, "loop", 1);
        verify(skills, atMost(8)).search(actor, "loop", 1);
    }
}
