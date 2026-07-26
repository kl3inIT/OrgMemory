package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessException;

public class KnowledgeAssetNotFoundException extends BusinessException {

    public KnowledgeAssetNotFoundException() {
        super(
                BusinessErrorCategory.NOT_FOUND,
                "knowledge.resource-not-available",
                "The requested knowledge resource is not available",
                BusinessErrorExposure.OPAQUE_RESOURCE);
    }
}
