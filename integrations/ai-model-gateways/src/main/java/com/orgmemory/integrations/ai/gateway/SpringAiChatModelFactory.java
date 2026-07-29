package com.orgmemory.integrations.ai.gateway;

import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Duration;
import org.springframework.ai.chat.model.ChatModel;

/**
 * Creates one Spring AI chat model for a concrete wire protocol.
 *
 * <p>Provider-specific SDK construction belongs behind this boundary. Routing,
 * credentials, caching, and workload selection remain provider-neutral.
 */
public interface SpringAiChatModelFactory {

    AiGatewayProtocol protocol();

    ChatModel create(Request request);

    record Request(
            String baseUrl,
            SecretValue credential,
            String modelId,
            Duration timeout) {

        @Override
        public String toString() {
            return "Request[baseUrl=%s, credential=<redacted>, modelId=%s, timeout=%s]"
                    .formatted(baseUrl, modelId, timeout);
        }
    }
}
