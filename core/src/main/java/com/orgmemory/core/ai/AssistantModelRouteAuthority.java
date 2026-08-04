package com.orgmemory.core.ai;

import java.util.UUID;

/** Server-created authority for one exact Assistant generation route. */
public sealed interface AssistantModelRouteAuthority
        permits DefaultAssistantModelRouteAuthority,
                CatalogAssistantModelRouteAuthority {

    UUID organizationId();
}
