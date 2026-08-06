package com.orgmemory.core.organization;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    List<AppUser> findByOrganizationIdOrderByName(UUID organizationId);

    List<AppUser> findByOrganizationIdAndIdIn(UUID organizationId, Collection<UUID> ids);

    List<AppUser> findByEmailIgnoreCase(String email);

    Optional<AppUser> findByOrganizationIdAndEmailIgnoreCase(UUID organizationId, String email);

    boolean existsByIdAndOrganizationId(UUID id, UUID organizationId);
}
