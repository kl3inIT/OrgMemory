package com.orgmemory.core.identityprovisioning;

import java.util.UUID;
import org.springframework.data.repository.Repository;

interface ProvisioningCredentialRepository
        extends Repository<ProvisioningCredential, UUID> {

    ProvisioningCredential save(ProvisioningCredential credential);
}
