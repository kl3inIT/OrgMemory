package com.orgmemory.core.organization;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
}
