package com.orgmemory.core.identityprovisioning;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

interface ScimUserResourceRepository extends Repository<ScimUserResource, UUID> {

    ScimUserResource save(ScimUserResource resource);

    Optional<ScimUserResource> findByIdAndOrganizationIdAndConnectionId(
            UUID id, UUID organizationId, UUID connectionId);

    Optional<ScimUserResource>
            findByOrganizationIdAndConnectionIdAndExternalId(
                    UUID organizationId, UUID connectionId, String externalId);

    Optional<ScimUserResource>
            findByOrganizationIdAndConnectionIdAndNormalizedUserName(
                    UUID organizationId, UUID connectionId, String normalizedUserName);

    boolean existsByOrganizationIdAndAppUserId(
            UUID organizationId, UUID appUserId);
}
