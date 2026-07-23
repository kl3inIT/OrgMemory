package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.AccessGate;
import java.util.UUID;

public record PromoteNormalizedRecordCommand(
        UUID organizationId,
        UUID knowledgeSpaceId,
        UUID sourceObjectId,
        UUID sourceRevisionId,
        UUID normalizedRecordId,
        AccessGate orgMemoryGate) {
}
