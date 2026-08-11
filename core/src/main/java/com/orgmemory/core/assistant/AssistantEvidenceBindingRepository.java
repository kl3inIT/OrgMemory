package com.orgmemory.core.assistant;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantEvidenceBindingRepository
        extends JpaRepository<AssistantEvidenceBinding, UUID> {

    Optional<AssistantEvidenceBinding>
            findByIdAndOrganizationIdAndConversationIdAndCreatedByUserId(
                    UUID id,
                    UUID organizationId,
                    UUID conversationId,
                    UUID createdByUserId);

    List<AssistantEvidenceBinding>
            findAllByIdInAndOrganizationIdAndConversationIdAndCreatedByUserId(
                    Collection<UUID> ids,
                    UUID organizationId,
                    UUID conversationId,
                    UUID createdByUserId);

    List<AssistantEvidenceBinding>
            findAllByOrganizationIdAndConversationIdAndCreatedByUserIdOrderByCreatedAtAsc(
                    UUID organizationId,
                    UUID conversationId,
                    UUID createdByUserId);
}
