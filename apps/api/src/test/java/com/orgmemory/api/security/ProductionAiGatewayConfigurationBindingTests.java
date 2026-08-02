package com.orgmemory.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.orgmemory.integrations.ai.gateway.AiGatewayProperties;
import com.orgmemory.integrations.ai.gateway.AiModelGatewayConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class ProductionAiGatewayConfigurationBindingTests {

    @Test
    void prodCredentialOverrideRetainsTheCompleteOpenAiGatewayDefinition() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(AiModelGatewayConfiguration.class)
                .withSystemProperties(
                        "OPENAI_API_KEY=redacted-base-key",
                        "ORGMEMORY_OPENAI_API_KEY=redacted-prod-key",
                        "ORGMEMORY_OPENAI_REASONING_EFFORT_SUPPORTED=false")
                .withPropertyValues("spring.profiles.active=prod")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertEquals(
                            "https://api.openai.com/v1",
                            context.getEnvironment().getProperty(
                                    "orgmemory.ai.gateways.openai.base-url"));
                    assertEquals(
                            "redacted-prod-key",
                            context.getEnvironment().getProperty(
                                    "orgmemory.ai.gateways.openai.api-key"));
                    AiGatewayProperties properties =
                            context.getBean(AiGatewayProperties.class);
                    AiGatewayProperties.Gateway gateway =
                            properties.gateways().get("openai");

                    assertNotNull(gateway);
                    assertEquals("https://api.openai.com/v1", gateway.baseUrl());
                    assertEquals("redacted-prod-key", gateway.apiKey());
                    assertFalse(gateway.supportsOpenAiReasoningEffort());
                });
    }
}
