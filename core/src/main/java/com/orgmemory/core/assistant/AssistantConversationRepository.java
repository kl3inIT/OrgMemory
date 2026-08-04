package com.orgmemory.core.assistant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

interface AssistantConversationRepository extends JpaRepository<AssistantConversation, UUID> {

    Optional<AssistantConversation> findByIdAndOrganizationIdAndActorUserId(
            UUID id, UUID organizationId, UUID actorUserId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AssistantConversation> findForUpdateByIdAndOrganizationIdAndActorUserId(
            UUID id, UUID organizationId, UUID actorUserId);

    List<AssistantConversation> findTop50ByOrganizationIdAndActorUserIdOrderByLastActivityAtDescIdDesc(
            UUID organizationId, UUID actorUserId);
}
