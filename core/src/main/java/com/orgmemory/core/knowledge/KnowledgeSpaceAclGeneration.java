package com.orgmemory.core.knowledge;

import java.util.UUID;

interface KnowledgeSpaceAclGeneration {

    UUID getKnowledgeSpaceId();

    long getAclGeneration();
}
