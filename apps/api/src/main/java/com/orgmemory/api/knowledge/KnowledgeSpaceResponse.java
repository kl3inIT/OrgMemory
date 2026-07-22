package com.orgmemory.api.knowledge;

import com.orgmemory.core.knowledge.KnowledgeSpaceTarget;
import java.util.UUID;

record KnowledgeSpaceResponse(
        UUID id,
        String key,
        String name,
        UUID departmentId) {

    static KnowledgeSpaceResponse from(KnowledgeSpaceTarget target) {
        return new KnowledgeSpaceResponse(
                target.id(), target.key(), target.name(), target.departmentId());
    }
}
