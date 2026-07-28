package com.orgmemory.integrations.ai.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.ai.AiGatewayPreset;
import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConfiguredAiGatewayEndpointPolicyTests {

    @Test
    void fixedProvidersCannotBeRedirectedToAnAttackerEndpoint() {
        var policy = policy(Set.of());

        assertThrows(
                BusinessValidationException.class,
                () -> policy.requireAllowed(
                        AiGatewayPreset.OPENAI,
                        AiGatewayProtocol.OPENAI_COMPATIBLE,
                        "https://attacker.example/v1"));
    }

    @Test
    void customEndpointsRequireAnExactOperatorAllowlistedOrigin() {
        var policy = policy(Set.of("https://gateway.example:8443"));

        assertEquals(
                "https://gateway.example:8443/v1",
                policy.requireAllowed(
                        AiGatewayPreset.LITELLM,
                        AiGatewayProtocol.OPENAI_COMPATIBLE,
                        "https://gateway.example:8443/v1/"));
        assertThrows(
                BusinessValidationException.class,
                () -> policy.requireAllowed(
                        AiGatewayPreset.LITELLM,
                        AiGatewayProtocol.OPENAI_COMPATIBLE,
                        "https://gateway.example/v1"));
    }

    @Test
    void aPresetCannotSwitchToAnUnimplementedProtocol() {
        var policy = policy(Set.of());

        assertThrows(
                BusinessValidationException.class,
                () -> policy.requireAllowed(
                        AiGatewayPreset.ANTHROPIC,
                        AiGatewayProtocol.OPENAI_COMPATIBLE,
                        "https://api.anthropic.com"));
    }

    private static ConfiguredAiGatewayEndpointPolicy policy(
            Set<String> allowedOrigins) {
        return new ConfiguredAiGatewayEndpointPolicy(
                new AiGatewayProperties(
                        Map.of(),
                        null,
                        allowedOrigins));
    }
}
