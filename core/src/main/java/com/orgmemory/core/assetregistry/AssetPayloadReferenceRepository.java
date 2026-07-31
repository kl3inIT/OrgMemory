package com.orgmemory.core.assetregistry;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetPayloadReferenceRepository
        extends JpaRepository<AssetPayloadReference, UUID> {

    Optional<AssetPayloadReference> findByDraftIdAndOrganizationId(
            UUID draftId, UUID organizationId);

    Optional<AssetPayloadReference> findByRevisionIdAndOrganizationId(
            UUID revisionId, UUID organizationId);

    Optional<AssetPayloadReference> findByReleaseIdAndOrganizationId(
            UUID releaseId, UUID organizationId);

    boolean existsByOrganizationIdAndReferenceValue(
            UUID organizationId, String referenceValue);
}
