package com.orgmemory.core.assistant;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssistantConversationMessageRepository
        extends JpaRepository<AssistantConversationMessage, UUID> {

    List<AssistantConversationMessage> findAllByConversationIdOrderBySequenceId(
            UUID conversationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AssistantConversationMessage>
            findByIdAndOrganizationIdAndActorUserIdAndRole(
                    UUID id,
                    UUID organizationId,
                    UUID actorUserId,
                    AssistantConversationRole role);

    @Query("""
            select message.conversationId as conversationId, count(message.id) as messageCount
            from AssistantConversationMessage message
            where message.conversationId in :conversationIds
            group by message.conversationId
            """)
    List<MessageCount> countByConversationIds(
            @Param("conversationIds") Collection<UUID> conversationIds);

    interface MessageCount {

        UUID getConversationId();

        long getMessageCount();
    }
}
