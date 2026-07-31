package com.orgmemory.core.knowledge.space;

import com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeSpacePort;
import com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeSpaceRef;
import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Space-owned authorization and directory adapter for source-ledger operations. */
@Service
class SourceKnowledgeSpaceAdapter implements SourceKnowledgeSpacePort {

    private final KnowledgeSpaceService spaces;

    SourceKnowledgeSpaceAdapter(KnowledgeSpaceService spaces) {
        this.spaces = spaces;
    }

    @Override
    public SourceKnowledgeSpaceRef requireUploadTarget(
            CurrentActor actor, UUID knowledgeSpaceId) {
        KnowledgeSpaceTarget target = spaces.requireUploadTarget(actor, knowledgeSpaceId);
        return new SourceKnowledgeSpaceRef(target.id(), target.departmentId());
    }

    @Override
    public void requireInOrganization(UUID organizationId, UUID knowledgeSpaceId) {
        spaces.requireInOrganization(organizationId, knowledgeSpaceId);
    }
}
