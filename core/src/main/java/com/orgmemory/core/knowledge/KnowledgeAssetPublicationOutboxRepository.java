package com.orgmemory.core.knowledge;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface KnowledgeAssetPublicationOutboxRepository
        extends JpaRepository<KnowledgeAssetPublicationOutbox, UUID> {

    Optional<KnowledgeAssetPublicationOutbox> findByKnowledgeAssetVersionId(
            UUID knowledgeAssetVersionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<KnowledgeAssetPublicationOutbox> findByIdAndOrganizationId(
            UUID id, UUID organizationId);

    @Query("""
            select publication
            from KnowledgeAssetPublicationOutbox publication
            where publication.status = com.orgmemory.core.knowledge.KnowledgeAssetPublicationStatus.APPLIED
              and (
                    publication.authorizationModelId is null
                    or publication.authorizationModelId <> :authorizationModelId
              )
            order by publication.updatedAt, publication.id
            """)
    java.util.List<KnowledgeAssetPublicationOutbox> findAuthorizationModelDrift(
            @Param("authorizationModelId") String authorizationModelId,
            Pageable pageable);
}
