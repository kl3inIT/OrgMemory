package com.orgmemory.core.assetregistry.api;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public class AssetConflictException extends BusinessException {

    public AssetConflictException(String message) {
        super(BusinessErrorCategory.CONFLICT, "asset.conflict", message);
    }

    public AssetConflictException(String message, Throwable cause) {
        super(BusinessErrorCategory.CONFLICT, "asset.conflict", message, cause);
    }
}
