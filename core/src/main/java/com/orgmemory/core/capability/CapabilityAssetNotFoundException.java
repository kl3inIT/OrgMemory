package com.orgmemory.core.capability;

import java.util.UUID;

public class CapabilityAssetNotFoundException extends RuntimeException {

    public CapabilityAssetNotFoundException(UUID id) {
        super("Capability asset not found: " + id);
    }
}
