package com.orgmemory.core.ai;

import java.util.UUID;

record CatalogAssistantModelRouteAuthority(
        UUID organizationId,
        UUID activationId,
        UUID gatewayProfileId,
        UUID routeOverrideId,
        long routeOverrideVersion) implements AssistantModelRouteAuthority {
}
