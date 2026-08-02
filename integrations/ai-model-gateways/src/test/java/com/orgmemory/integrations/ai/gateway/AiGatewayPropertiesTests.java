package com.orgmemory.integrations.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiGatewayAdministrationService;
import com.orgmemory.core.ai.AiGatewayCapability;
import com.orgmemory.core.ai.AiGatewayConnection;
import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.ai.OpenAiReasoningEffort;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

class AiGatewayPropertiesTests {

    @Test
    void defaultChatRoutesKeepGraphExtractionIndependent() {
        var properties = new AiGatewayProperties(null, null, null);

        assertEquals("gpt-5.6-sol", properties.route(AiWorkload.ASSISTANT_CHAT).modelId());
        assertEquals("gpt-5.6-sol", properties.route(AiWorkload.KEYWORD_PLANNING).modelId());
        assertEquals("gpt-5.4-mini", properties.route(AiWorkload.GRAPH_EXTRACTION).modelId());
    }

    @Test
    void queryAndDocumentEmbeddingShareOneRoute() {
        var properties = properties(Set.of(AiGatewayCapability.CHAT, AiGatewayCapability.EMBEDDING));

        assertEquals(
                properties.route(AiWorkload.QUERY_EMBEDDING),
                properties.route(AiWorkload.DOCUMENT_EMBEDDING));
    }

    @Test
    void keywordPlanningCanUseAnIndependentChatRoute() {
        var configured = new AiGatewayProperties(
                Map.of("openai", new AiGatewayProperties.Gateway(
                        "OpenAI",
                        "https://api.openai.com/v1",
                        "top-secret-key",
                        Set.of(AiGatewayCapability.CHAT),
                        Duration.ofSeconds(60))),
                new AiGatewayProperties.Routes(
                        new AiGatewayProperties.Route(
                                "openai",
                                "assistant-model"),
                        new AiGatewayProperties.Route(
                                "openai",
                                "keyword-model"),
                        new AiGatewayProperties.Route(
                                "openai",
                                "graph-model"),
                        new AiGatewayProperties.Route(
                                "openai",
                                "embedding-model")),
                Set.of());

        assertEquals(
                "keyword-model",
                configured.route(AiWorkload.KEYWORD_PLANNING).modelId());
        assertEquals(
                "assistant-model",
                configured.route(AiWorkload.ASSISTANT_CHAT).modelId());
    }

    @Test
    void credentialNeverAppearsInConfigurationRendering() {
        var properties = properties(Set.of(AiGatewayCapability.CHAT));

        assertFalse(properties.toString().contains("top-secret-key"));
        assertFalse(properties.gateways().get("openai").toString().contains("top-secret-key"));
    }

    @Test
    void workloadCapabilityIsValidatedBeforeProviderUse() {
        var registry = registry(properties(Set.of(AiGatewayCapability.CHAT)));

        assertThrows(
                AiGatewayUnavailableException.class,
                () -> registry.resolve(AiWorkload.QUERY_EMBEDDING));
    }

    @Test
    void deploymentGatewayMustDeclareOpenAiReasoningSupport() {
        var configured = new AiGatewayProperties(
                Map.of("openai", new AiGatewayProperties.Gateway(
                        "Compatible",
                        "https://provider.example/v1",
                        "top-secret-key",
                        Set.of(AiGatewayCapability.CHAT),
                        Duration.ofSeconds(60),
                        false)),
                new AiGatewayProperties.Routes(
                        new AiGatewayProperties.Route(
                                "openai",
                                "gpt-5.6-luna",
                                OpenAiReasoningEffort.NONE),
                        null,
                        null,
                        null),
                Set.of());

        assertThrows(
                AiGatewayUnavailableException.class,
                () -> registry(configured).resolve(AiWorkload.ASSISTANT_CHAT));
    }

    @Test
    void chatModelsAreCachedByCompleteRouteInsteadOfGatewayOnly() {
        var configured = new AiGatewayProperties(
                Map.of("openai", new AiGatewayProperties.Gateway(
                        "OpenAI",
                        "https://api.openai.com/v1",
                        "top-secret-key",
                        Set.of(AiGatewayCapability.CHAT),
                        Duration.ofSeconds(60))),
                new AiGatewayProperties.Routes(
                        new AiGatewayProperties.Route("openai", "assistant-model"),
                        new AiGatewayProperties.Route("openai", "graph-model"),
                        new AiGatewayProperties.Route("openai", "embedding-model")),
                Set.of());
        SpringAiChatModelFactory factory = new SpringAiChatModelFactory() {
            @Override
            public AiGatewayProtocol protocol() {
                return AiGatewayProtocol.OPENAI_COMPATIBLE;
            }

            @Override
            public ChatModel create(Request request) {
                return mock(ChatModel.class);
            }
        };
        var provider = new SpringAiChatModelProvider(
                registry(configured),
                new SpringAiChatModelFactories(List.of(factory)));

        assertNotSame(
                provider.resolve(AiWorkload.ASSISTANT_CHAT),
                provider.resolve(AiWorkload.GRAPH_EXTRACTION));
    }

    @Test
    void anExplicitOrganizationRouteFailsClosedWhenItsGatewayIsUnavailable() {
        var administration = mock(AiGatewayAdministrationService.class);
        var organizationId = UUID.randomUUID();
        var profileId = UUID.randomUUID();
        when(administration.route(organizationId, AiWorkload.ASSISTANT_CHAT))
                .thenReturn(Optional.of(new com.orgmemory.core.ai.AiRouteOverrideView(
                        UUID.randomUUID(),
                        AiWorkload.ASSISTANT_CHAT,
                        profileId,
                        "private-gateway",
                        "private-model",
                        1,
                        UUID.randomUUID(),
                        java.time.Instant.now())));
        when(administration.connection(organizationId, "private-gateway"))
                .thenReturn(Optional.empty());
        var registry = new AiGatewayRegistry(
                properties(Set.of(AiGatewayCapability.CHAT)),
                administration);

        assertThrows(
                AiGatewayUnavailableException.class,
                () -> registry.resolve(
                        organizationId,
                        AiWorkload.ASSISTANT_CHAT));
    }

    @Test
    void aCollidingOrganizationGatewayKeyDoesNotReplaceTheDeploymentDefault() {
        var administration = mock(AiGatewayAdministrationService.class);
        var organizationId = UUID.randomUUID();
        when(administration.route(
                        organizationId,
                        AiWorkload.ASSISTANT_CHAT))
                .thenReturn(Optional.empty());
        when(administration.connection(organizationId, "openai"))
                .thenReturn(Optional.of(new AiGatewayConnection(
                        organizationId,
                        UUID.randomUUID(),
                        "openai",
                        AiGatewayProtocol.OPENAI_COMPATIBLE,
                        "https://organization.example/v1",
                        SecretValue.of("organization-secret"),
                        Duration.ofSeconds(10),
                        7)));
        var registry = new AiGatewayRegistry(
                properties(Set.of(AiGatewayCapability.CHAT)),
                administration);

        AiGatewayRegistry.ResolvedGateway resolved = registry.definition(
                organizationId,
                AiWorkload.ASSISTANT_CHAT,
                registry.resolve(
                        organizationId,
                        AiWorkload.ASSISTANT_CHAT));

        assertEquals("https://api.openai.com/v1", resolved.baseUrl());
        assertEquals(0, resolved.profileVersion());
    }

    private static AiGatewayProperties properties(Set<AiGatewayCapability> capabilities) {
        return new AiGatewayProperties(
                Map.of("openai", new AiGatewayProperties.Gateway(
                        "OpenAI",
                        "https://api.openai.com/v1",
                        "top-secret-key",
                        capabilities,
                        Duration.ofSeconds(60))),
                new AiGatewayProperties.Routes(
                        new AiGatewayProperties.Route("openai", "gpt-5.6-sol"),
                        new AiGatewayProperties.Route("openai", "gpt-5.6-sol"),
                        new AiGatewayProperties.Route("openai", "text-embedding-3-large")),
                Set.of());
    }

    private static AiGatewayRegistry registry(AiGatewayProperties properties) {
        return new AiGatewayRegistry(
                properties,
                mock(AiGatewayAdministrationService.class));
    }
}
