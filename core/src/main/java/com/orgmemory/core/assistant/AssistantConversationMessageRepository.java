package com.orgmemory.core.assistant;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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

    Optional<AssistantConversationMessage>
            findOneByIdAndOrganizationIdAndActorUserIdAndRole(
                    UUID id,
                    UUID organizationId,
                    UUID actorUserId,
                    AssistantConversationRole role);

    /**
     * Identifies the most recent turns holding both a question and an answer,
     * newest first. A partial unique index over turn and role means two rows
     * are exactly one of each, so the count is the completeness test. A turn
     * still streaming, or one that failed before answering, holds one row and
     * is excluded here rather than recognized later.
     */
    @Query("""
            select message.turnId
            from AssistantConversationMessage message
            where message.conversationId = :conversationId
              and message.organizationId = :organizationId
              and message.turnId is not null
            group by message.turnId
            having count(message.id) = 2
            order by min(message.sequenceId) desc
            """)
    List<UUID> findRecentCompletedTurnIds(
            @Param("organizationId") UUID organizationId,
            @Param("conversationId") UUID conversationId,
            Pageable limit);

    List<AssistantConversationMessage> findAllByTurnIdInOrderBySequenceId(
            Collection<UUID> turnIds);

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
