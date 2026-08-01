package com.orgmemory.core.knowledge.acl;

import java.util.UUID;

/** Maximum current source ACL generation contributing to one Knowledge Space. */
public record KnowledgeSpaceAclGenerationRef(
        UUID knowledgeSpaceId,
        long aclGeneration) {
}
