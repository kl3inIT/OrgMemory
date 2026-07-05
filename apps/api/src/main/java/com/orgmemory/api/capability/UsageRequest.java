package com.orgmemory.api.capability;

import com.orgmemory.core.capability.UsageEventType;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record UsageRequest(UUID userId, @NotNull UsageEventType eventType, String metadataJson) {
}
