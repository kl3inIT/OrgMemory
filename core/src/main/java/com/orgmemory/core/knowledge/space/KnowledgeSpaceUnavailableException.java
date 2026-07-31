package com.orgmemory.core.knowledge.space;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public class KnowledgeSpaceUnavailableException extends BusinessException {

    public KnowledgeSpaceUnavailableException(String message) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                "knowledge-space.unavailable",
                message);
    }
}
