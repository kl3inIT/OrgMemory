package com.orgmemory.core.assetregistry;

public class AssetConflictException extends RuntimeException {

    public AssetConflictException(String message) {
        super(message);
    }

    public AssetConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
