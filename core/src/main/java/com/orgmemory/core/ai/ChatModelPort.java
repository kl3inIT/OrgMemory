package com.orgmemory.core.ai;

import java.util.UUID;
import reactor.core.publisher.Flux;

public interface ChatModelPort {

    Flux<String> stream(AiWorkload workload, ChatGenerationRequest request);

    default Flux<String> stream(
            UUID organizationId,
            AiWorkload workload,
            ChatGenerationRequest request) {
        return stream(workload, request);
    }

    default Flux<String> stream(
            AiWorkload workload,
            ChatGenerationRequest request,
            String conversationId) {
        return stream(workload, request);
    }

    default Flux<String> stream(
            UUID organizationId,
            AiWorkload workload,
            ChatGenerationRequest request,
            String conversationId) {
        return stream(workload, request, conversationId);
    }

    default Flux<String> stream(
            AiWorkload workload,
            AiRoute route,
            ChatGenerationRequest request) {
        return stream(workload, request);
    }

    default Flux<String> stream(
            UUID organizationId,
            AiWorkload workload,
            AiRoute route,
            ChatGenerationRequest request) {
        return stream(workload, route, request);
    }

    /**
     * Streams one Assistant turn through an exact server-authorized route while retaining
     * bounded conversation memory. Implementations must fail closed rather than discard the
     * authority or conversation identity.
     */
    default Flux<String> stream(
            AssistantModelRouteAuthority authority,
            ChatGenerationRequest request,
            String conversationId,
            UUID actorUserId,
            String requestId) {
        return Flux.error(new UnsupportedOperationException(
                "Exact Assistant route authority is not supported"));
    }
}
