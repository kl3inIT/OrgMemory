package com.orgmemory.core.assistant;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantAnswerFeedbackRepository
        extends JpaRepository<AssistantAnswerFeedback, UUID> {

    List<AssistantAnswerFeedback> findAllByMessageIdIn(Collection<UUID> messageIds);
}
