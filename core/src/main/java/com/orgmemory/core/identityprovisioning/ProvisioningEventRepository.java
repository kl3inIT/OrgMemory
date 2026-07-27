package com.orgmemory.core.identityprovisioning;

import java.util.UUID;
import org.springframework.data.repository.Repository;

interface ProvisioningEventRepository extends Repository<ProvisioningEvent, UUID> {

    ProvisioningEvent save(ProvisioningEvent event);
}
