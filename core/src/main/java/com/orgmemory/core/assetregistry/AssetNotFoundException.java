package com.orgmemory.core.assetregistry;

public class AssetNotFoundException extends RuntimeException {

    public AssetNotFoundException() {
        super("The requested Asset is not available");
    }
}
