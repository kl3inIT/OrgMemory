package com.orgmemory.core.knowledge.space;

import java.util.UUID;

public interface KnowledgeSpaceAclGeneration {

    UUID getKnowledgeSpaceId();

    long getAclGeneration();
}
