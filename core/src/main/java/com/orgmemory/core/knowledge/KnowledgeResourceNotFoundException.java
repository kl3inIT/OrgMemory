package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessException;

/** Opaque not-found result for absent or cross-tenant knowledge resources. */
public final class KnowledgeResourceNotFoundException extends BusinessException {

    public KnowledgeResourceNotFoundException() {
        super(
                BusinessErrorCategory.NOT_FOUND,
                "knowledge.resource-not-available",
                "The requested knowledge resource is not available",
                BusinessErrorExposure.OPAQUE_RESOURCE);
    }
}
