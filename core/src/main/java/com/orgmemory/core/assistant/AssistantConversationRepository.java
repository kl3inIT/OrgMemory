package com.orgmemory.core.assistant;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantConversationRepository extends JpaRepository<AssistantConversation, UUID> {

    Optional<AssistantConversation> findByIdAndOrganizationIdAndActorUserId(
            UUID id, UUID organizationId, UUID actorUserId);

    List<AssistantConversation> findTop50ByOrganizationIdAndActorUserIdOrderByLastActivityAtDescIdDesc(
            UUID organizationId, UUID actorUserId);
}
