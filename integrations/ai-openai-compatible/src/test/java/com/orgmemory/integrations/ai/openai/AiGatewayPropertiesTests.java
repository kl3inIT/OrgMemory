package com.orgmemory.integrations.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.ai.AiGatewayCapability;
import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiWorkload;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiGatewayPropertiesTests {

    @Test
    void queryAndDocumentEmbeddingShareOneRoute() {
        var properties = properties(Set.of(AiGatewayCapability.CHAT, AiGatewayCapability.EMBEDDING));

        assertEquals(
                properties.route(AiWorkload.QUERY_EMBEDDING),
                properties.route(AiWorkload.DOCUMENT_EMBEDDING));
    }

    @Test
    void credentialNeverAppearsInConfigurationRendering() {
        var properties = properties(Set.of(AiGatewayCapability.CHAT));

        assertFalse(properties.toString().contains("top-secret-key"));
        assertFalse(properties.gateways().get("openai").toString().contains("top-secret-key"));
    }

    @Test
    void workloadCapabilityIsValidatedBeforeProviderUse() {
        var registry = new AiGatewayRegistry(properties(Set.of(AiGatewayCapability.CHAT)));

        assertThrows(
                AiGatewayUnavailableException.class,
                () -> registry.resolve(AiWorkload.QUERY_EMBEDDING));
    }

    private static AiGatewayProperties properties(Set<AiGatewayCapability> capabilities) {
        return new AiGatewayProperties(
                Map.of("openai", new AiGatewayProperties.Gateway(
                        "OpenAI",
                        "https://api.openai.com",
                        "top-secret-key",
                        capabilities,
                        Duration.ofSeconds(60))),
                new AiGatewayProperties.Routes(
                        new AiGatewayProperties.Route("openai", "gpt-5.5"),
                        new AiGatewayProperties.Route("openai", "text-embedding-3-large")));
    }
}
