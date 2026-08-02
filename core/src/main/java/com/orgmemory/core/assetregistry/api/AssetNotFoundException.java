package com.orgmemory.core.assetregistry.api;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessException;

public class AssetNotFoundException extends BusinessException {

    public AssetNotFoundException() {
        this(null);
    }

    public AssetNotFoundException(Throwable cause) {
        super(
                BusinessErrorCategory.NOT_FOUND,
                "knowledge.resource-not-available",
                "The requested knowledge resource is not available",
                BusinessErrorExposure.OPAQUE_RESOURCE,
                cause);
    }
}
