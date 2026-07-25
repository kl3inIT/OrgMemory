package com.orgmemory.core.assetregistry;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<Asset> findByOrganizationIdAndNamespaceAndSlug(
            UUID organizationId, String namespace, String slug);

    List<Asset> findByOrganizationIdAndIdInAndAuthorizationReadyTrueOrderByNamespaceAscSlugAsc(
            UUID organizationId, Collection<UUID> ids);
}
