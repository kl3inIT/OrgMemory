package com.orgmemory.core.assistant;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssistantTurnFileRepository extends JpaRepository<AssistantTurnFile, UUID> {}
