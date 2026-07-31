package com.orgmemory.core.assetregistry;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PromptEvaluationRunRepository extends JpaRepository<PromptEvaluationRun, UUID> {
}
