package com.orgmemory.integrations.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiWorkload;
import org.junit.jupiter.api.Test;

class ModelKeyTests {

    @Test
    void globalModelKeysCanEvictSupersededProfiles() {
        ModelKey previous = new ModelKey(
                null,
                AiWorkload.ASSISTANT_CHAT,
                new AiRoute("gateway", "model"),
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                1);
        ModelKey active = new ModelKey(
                null,
                AiWorkload.ASSISTANT_CHAT,
                new AiRoute("gateway", "model"),
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                2);

        assertTrue(previous.supersededBy(active));
    }
}
