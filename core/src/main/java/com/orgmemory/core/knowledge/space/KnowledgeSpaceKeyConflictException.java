package com.orgmemory.core.knowledge.space;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

/** A Knowledge Space name that derives a key another space in the organization already holds. */
public class KnowledgeSpaceKeyConflictException extends BusinessException {

    public KnowledgeSpaceKeyConflictException(String message) {
        super(
                BusinessErrorCategory.CONFLICT,
                "knowledge-space.key-conflict",
                message);
    }
}
