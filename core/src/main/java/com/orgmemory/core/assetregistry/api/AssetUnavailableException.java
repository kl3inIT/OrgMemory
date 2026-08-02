package com.orgmemory.core.assetregistry.api;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public class AssetUnavailableException extends BusinessException {

    public AssetUnavailableException(String message) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                "asset.unavailable",
                message);
    }

    public AssetUnavailableException(String message, Throwable cause) {
        super(
                BusinessErrorCategory.UNAVAILABLE,
                "asset.unavailable",
                message,
                cause);
    }
}
