package com.orgmemory.core.assistant;

import com.orgmemory.core.ai.AssistantModelRouteAuthority;
import com.orgmemory.core.ai.ChatGenerationRequest;
import com.orgmemory.core.organization.CurrentActor;
import java.util.function.Consumer;
import reactor.core.publisher.Flux;

/** Provider adapter boundary for one request-local, server-owned agent loop. */
public interface AssistantAgentModelPort {

    Flux<String> stream(
            AssistantModelRouteAuthority authority,
            ChatGenerationRequest request,
            String conversationId,
            CurrentActor actor,
            String requestId,
            Consumer<AssistantAgentActivity> activities);
}
