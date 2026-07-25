package com.orgmemory.core.assetregistry;

public class AssetUnavailableException extends RuntimeException {

    public AssetUnavailableException(String message) {
        super(message);
    }

    public AssetUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
