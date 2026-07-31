package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;

/** Outbound boundary for source-ledger operations that target a Knowledge Space. */
public interface SourceKnowledgeSpacePort {

    SourceKnowledgeSpaceRef requireUploadTarget(
            CurrentActor actor, UUID knowledgeSpaceId);

    void requireInOrganization(UUID organizationId, UUID knowledgeSpaceId);
}
