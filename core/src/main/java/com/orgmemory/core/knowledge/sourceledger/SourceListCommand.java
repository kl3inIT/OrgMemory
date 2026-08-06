package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.UUID;

public record SourceListCommand(
        UUID knowledgeSpaceId,
        KnowledgeClassification classification,
        SourceListStatus status,
        String query,
        String cursor,
        int pageSize) {
}
