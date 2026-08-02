package com.orgmemory.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class AiRouteTests {

    @Test
    void omittedOpenAiReasoningEffortMeansProviderDefault() {
        AiRoute route = new AiRoute("OPENAI", "gpt-5.6-sol");

        assertEquals("openai", route.gatewayId());
        assertNull(route.openAiReasoningEffort());
    }

    @Test
    void openAiReasoningEffortIsPartOfRouteAndCacheIdentity() {
        AiRoute providerDefault = new AiRoute("openai", "gpt-5.6-luna");
        AiRoute disabled = new AiRoute(
                "openai",
                "gpt-5.6-luna",
                OpenAiReasoningEffort.NONE);

        assertNotEquals(providerDefault, disabled);
        assertEquals("none", disabled.openAiReasoningEffort().wireValue());
    }
}
