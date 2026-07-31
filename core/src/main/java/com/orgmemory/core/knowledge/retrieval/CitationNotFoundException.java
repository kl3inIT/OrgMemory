package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessException;

/**
 * Opaque citation lookup failure. Missing and unauthorized citations share the
 * same result so callers cannot use the endpoint as an existence oracle.
 */
public final class CitationNotFoundException extends BusinessException {

    public CitationNotFoundException() {
        super(
                BusinessErrorCategory.NOT_FOUND,
                "knowledge.resource-not-available",
                "The requested knowledge resource is not available",
                BusinessErrorExposure.OPAQUE_RESOURCE);
    }
}
