package com.orgmemory.core.identityprovisioning;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public final class ProvisioningConflictException extends BusinessException {

    public ProvisioningConflictException(String message) {
        super(
                BusinessErrorCategory.CONFLICT,
                "provisioning.resource-conflict",
                message);
    }
}
