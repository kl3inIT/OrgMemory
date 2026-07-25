package com.orgmemory.core.assistant;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantAssetFeedbackRepository
        extends JpaRepository<AssistantAssetFeedback, UUID> {
}
