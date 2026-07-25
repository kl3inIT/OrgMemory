package com.orgmemory.core.ai;

import reactor.core.publisher.Flux;

public interface ChatModelPort {

    Flux<String> stream(AiWorkload workload, ChatGenerationRequest request);

    default Flux<String> stream(
            AiWorkload workload,
            AiRoute route,
            ChatGenerationRequest request) {
        return stream(workload, request);
    }
}
