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
}
