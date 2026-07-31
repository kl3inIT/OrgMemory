package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.permission.AccessGate;
import java.util.UUID;

/** Validated source-ledger facts needed by the asset module to create one version. */
public record KnowledgeAssetPromotionRequest(
        UUID organizationId,
        UUID knowledgeSpaceId,
        UUID sourceObjectId,
        UUID sourceRevisionId,
        UUID normalizedRecordId,
        AccessGate orgMemoryGate) {
}
