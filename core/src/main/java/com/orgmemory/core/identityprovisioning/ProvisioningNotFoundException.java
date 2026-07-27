package com.orgmemory.core.identityprovisioning;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessException;

public final class ProvisioningNotFoundException extends BusinessException {

    public ProvisioningNotFoundException(String message) {
        super(
                BusinessErrorCategory.NOT_FOUND,
                "provisioning.resource-not-found",
                message);
    }
}
