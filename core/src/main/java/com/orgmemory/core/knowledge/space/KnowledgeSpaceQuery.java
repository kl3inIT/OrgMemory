package com.orgmemory.core.knowledge.space;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only Space boundary for consumers that do not own Space persistence. */
@Service
@Transactional(readOnly = true)
public class KnowledgeSpaceQuery {

    private final KnowledgeSpaceRepository spaces;

    KnowledgeSpaceQuery(KnowledgeSpaceRepository spaces) {
        this.spaces = spaces;
    }

    public boolean exists(UUID organizationId, UUID knowledgeSpaceId) {
        return spaces.existsByIdAndOrganizationId(knowledgeSpaceId, organizationId);
    }

    public boolean isActive(UUID organizationId, UUID knowledgeSpaceId) {
        return spaces.existsByIdAndOrganizationIdAndActiveTrue(
                knowledgeSpaceId, organizationId);
    }
}
