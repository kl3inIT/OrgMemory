package com.orgmemory.core.knowledge;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface KnowledgeAssetPublicationOutboxRepository
        extends JpaRepository<KnowledgeAssetPublicationOutbox, UUID> {

    Optional<KnowledgeAssetPublicationOutbox> findByKnowledgeAssetId(UUID knowledgeAssetId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<KnowledgeAssetPublicationOutbox> findByIdAndOrganizationId(
            UUID id, UUID organizationId);
}
