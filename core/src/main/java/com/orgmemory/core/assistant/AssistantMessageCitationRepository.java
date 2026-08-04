package com.orgmemory.core.assistant;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantMessageCitationRepository
        extends JpaRepository<AssistantMessageCitation, UUID> {

    List<AssistantMessageCitation> findAllByMessageIdOrderByCitationNumber(UUID messageId);
}
