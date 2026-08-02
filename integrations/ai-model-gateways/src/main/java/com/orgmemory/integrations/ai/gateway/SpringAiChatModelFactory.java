package com.orgmemory.integrations.ai.gateway;

import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.ai.OpenAiReasoningEffort;
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
            OpenAiReasoningEffort openAiReasoningEffort,
            Duration timeout) {

        public Request(
                String baseUrl,
                SecretValue credential,
                String modelId,
                Duration timeout) {
            this(baseUrl, credential, modelId, null, timeout);
        }

        @Override
        public String toString() {
            return "Request[baseUrl=%s, credential=<redacted>, modelId=%s, openAiReasoningEffort=%s, timeout=%s]"
                    .formatted(
                            baseUrl,
                            modelId,
                            openAiReasoningEffort,
                            timeout);
        }
    }
}
