package com.orgmemory.core.ai;

import java.util.UUID;

record DefaultAssistantModelRouteAuthority(
        UUID organizationId,
        AiRoute route,
        UUID routeOverrideId,
        long routeOverrideVersion) implements AssistantModelRouteAuthority {
}
