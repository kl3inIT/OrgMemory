package com.orgmemory.core.knowledge.sourceledger;

import java.util.UUID;

/** Source-owned identity needed to mark one staged revision as published. */
public record PublishSourceRevisionCommand(
        UUID organizationId,
        UUID sourceObjectId,
        UUID sourceRevisionId) {
}
