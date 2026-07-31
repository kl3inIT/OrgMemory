package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public class KnowledgeIngestionConflictException extends BusinessException {

    public KnowledgeIngestionConflictException(String message) {
        super(
                BusinessErrorCategory.CONFLICT,
                "knowledge-ingestion.conflict",
                message);
    }
}
