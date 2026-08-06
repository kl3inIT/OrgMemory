package com.orgmemory.core.knowledge.space;

import com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeSpacePort;
import com.orgmemory.core.knowledge.sourceledger.SourceKnowledgeSpaceRef;
import com.orgmemory.core.organization.CurrentActor;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Space-owned authorization and directory adapter for source-ledger operations. */
@Service
class SourceKnowledgeSpaceAdapter implements SourceKnowledgeSpacePort {

    private final KnowledgeSpaceService spaces;
    private final KnowledgeSpaceRepository repository;

    SourceKnowledgeSpaceAdapter(
            KnowledgeSpaceService spaces,
            KnowledgeSpaceRepository repository) {
        this.spaces = spaces;
        this.repository = repository;
    }

    @Override
    public SourceKnowledgeSpaceRef requireUploadTarget(
            CurrentActor actor, UUID knowledgeSpaceId) {
        KnowledgeSpaceTarget target = spaces.requireUploadTarget(actor, knowledgeSpaceId);
        return new SourceKnowledgeSpaceRef(
                target.id(), target.key(), target.name(), target.departmentId());
    }

    @Override
    public Map<UUID, SourceKnowledgeSpaceRef> describeAll(
            UUID organizationId, Collection<UUID> knowledgeSpaceIds) {
        if (knowledgeSpaceIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, SourceKnowledgeSpaceRef> result = new LinkedHashMap<>();
        repository.findByOrganizationIdAndIdInOrderByName(organizationId, knowledgeSpaceIds)
                .forEach(space -> result.put(
                        space.getId(),
                        new SourceKnowledgeSpaceRef(
                                space.getId(),
                                space.getKey(),
                                space.getName(),
                                space.getDepartmentId())));
        return Map.copyOf(result);
    }

    @Override
    public void requireInOrganization(UUID organizationId, UUID knowledgeSpaceId) {
        spaces.requireInOrganization(organizationId, knowledgeSpaceId);
    }
}
