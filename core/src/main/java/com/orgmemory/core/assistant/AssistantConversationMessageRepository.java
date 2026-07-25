package com.orgmemory.core.assistant;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantConversationMessageRepository
        extends JpaRepository<AssistantConversationMessage, UUID> {

    List<AssistantConversationMessage> findAllByConversationIdOrderBySequenceId(
            UUID conversationId);

    long countByConversationId(UUID conversationId);
}
