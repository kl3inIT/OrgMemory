package com.orgmemory.api.scim;

import com.orgmemory.core.identityprovisioning.ProvisioningOperationalState;
import java.util.UUID;

public record ScimMachinePrincipal(
        UUID organizationId,
        UUID connectionId,
        UUID credentialId,
        String publicTokenId,
        ProvisioningOperationalState connectionState) {
}
