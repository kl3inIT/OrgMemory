package com.orgmemory.integrations.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.AssistantModelAuthorityService;
import com.orgmemory.core.assetregistry.skill.SkillRuntimeOperations;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.secret.SecretValue;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.chat.client.autoconfigure.ChatClientBuilderConfigurer;
import org.springframework.beans.factory.ObjectProvider;

class SpringAiChatModelAdapterTests {

    @Test
    void generalMemoryClientDoesNotPopulateAssistantMemoryCache() throws Exception {
        Fixture fixture = fixture();

        ChatClient general = memoryClient(fixture);
        ChatClient assistant = assistantMemoryClient(fixture);

        assertNotSame(general, assistant);
        verify(fixture.models()).resolve(
                fixture.organizationId(),
                AiWorkload.ASSISTANT_CHAT,
                fixture.route());
        verify(fixture.models()).resolveAssistant(
                fixture.organizationId(),
                fixture.route(),
                fixture.gateway());
    }

    @Test
    void assistantMemoryClientDoesNotPopulateGeneralMemoryCache() throws Exception {
        Fixture fixture = fixture();

        ChatClient assistant = assistantMemoryClient(fixture);
        ChatClient general = memoryClient(fixture);

        assertNotSame(assistant, general);
        verify(fixture.models()).resolveAssistant(
                fixture.organizationId(),
                fixture.route(),
                fixture.gateway());
        verify(fixture.models()).resolve(
                fixture.organizationId(),
                AiWorkload.ASSISTANT_CHAT,
                fixture.route());
    }

    private static Fixture fixture() {
        UUID organizationId = UUID.randomUUID();
        AiRoute route = new AiRoute("assistant", "model");
        AiGatewayRegistry.ResolvedGateway gateway = new AiGatewayRegistry.ResolvedGateway(
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                false,
                "https://example.test",
                SecretValue.of("test-credential"),
                Duration.ofSeconds(5),
                1);
        SpringAiChatModelProvider models = mock(SpringAiChatModelProvider.class);
        when(models.resolve(organizationId, AiWorkload.ASSISTANT_CHAT, route))
                .thenReturn(mock(ChatModel.class));
        when(models.resolveAssistant(organizationId, route, gateway))
                .thenReturn(mock(ChatModel.class));

        @SuppressWarnings("unchecked")
        ObjectProvider<ChatMemory> memory = mock(ObjectProvider.class);
        when(memory.getIfAvailable()).thenReturn(mock(ChatMemory.class));
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatClientBuilderConfigurer> configurer = mock(ObjectProvider.class);
        when(configurer.getIfAvailable()).thenReturn(null);

        SpringAiChatModelAdapter adapter = new SpringAiChatModelAdapter(
                mock(AiGatewayRegistry.class),
                models,
                memory,
                configurer,
                mock(AssistantModelAuthorityService.class),
                mock(PermissionAuditService.class),
                mock(SkillRuntimeOperations.class));
        return new Fixture(adapter, models, organizationId, route, gateway);
    }

    private static ChatClient memoryClient(Fixture fixture) throws Exception {
        Method method = SpringAiChatModelAdapter.class.getDeclaredMethod(
                "memoryClient",
                UUID.class,
                AiWorkload.class,
                AiRoute.class,
                AiGatewayRegistry.ResolvedGateway.class);
        method.setAccessible(true);
        return (ChatClient) method.invoke(
                fixture.adapter(),
                fixture.organizationId(),
                AiWorkload.ASSISTANT_CHAT,
                fixture.route(),
                fixture.gateway());
    }

    private static ChatClient assistantMemoryClient(Fixture fixture) throws Exception {
        Method method = SpringAiChatModelAdapter.class.getDeclaredMethod(
                "assistantMemoryClient",
                UUID.class,
                AiRoute.class,
                AiGatewayRegistry.ResolvedGateway.class);
        method.setAccessible(true);
        return (ChatClient) method.invoke(
                fixture.adapter(),
                fixture.organizationId(),
                fixture.route(),
                fixture.gateway());
    }

    private record Fixture(
            SpringAiChatModelAdapter adapter,
            SpringAiChatModelProvider models,
            UUID organizationId,
            AiRoute route,
            AiGatewayRegistry.ResolvedGateway gateway) { }
}
