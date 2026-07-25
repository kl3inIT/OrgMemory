package com.orgmemory.core.assetregistry;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PromptEvaluationRunRepository extends JpaRepository<PromptEvaluationRun, UUID> {

    Optional<PromptEvaluationRun> findFirstByReleaseIdAndOrganizationIdOrderByEvaluatedAtDesc(
            UUID releaseId, UUID organizationId);
}
