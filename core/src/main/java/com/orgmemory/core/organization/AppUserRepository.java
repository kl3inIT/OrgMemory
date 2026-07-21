package com.orgmemory.core.organization;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    List<AppUser> findByOrganizationIdOrderByName(UUID organizationId);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}
