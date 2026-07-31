package com.orgmemory.core.knowledge.sourceledger;

import java.util.Optional;
import java.util.UUID;

/** Outbound boundary used by the source ledger to persist a validated asset promotion. */
public interface KnowledgeAssetPromotionPort {

    Optional<SourceKnowledgeAssetRef> findByNormalizedRecord(
            UUID organizationId, UUID knowledgeSpaceId, UUID normalizedRecordId);

    SourceKnowledgeAssetRef promote(KnowledgeAssetPromotionRequest request);
}
