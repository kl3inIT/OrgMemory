package com.orgmemory.core.assetregistry.prompt;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class PromptRunCoordinator {

    private final PromptRunRepository runs;
    private final PromptEvaluationRunRepository evaluations;

    PromptRunCoordinator(
            PromptRunRepository runs,
            PromptEvaluationRunRepository evaluations) {
        this.runs = runs;
        this.evaluations = evaluations;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    UUID start(
            CurrentActor actor,
            AssetConsumptionRelease release,
            AiRoute route,
            String inputShapeDigest,
            String citationRefs,
            Instant startedAt) {
        return runs.saveAndFlush(new PromptRun(
                        actor.organizationId(),
                        release.assetId(),
                        release.releaseId(),
                        release.digest(),
                        actor.userId(),
                        route,
                        inputShapeDigest,
                        citationRefs,
                        startedAt))
                .getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void succeed(UUID runId, String sanitizedOutcome, Instant completedAt) {
        PromptRun run = runs.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Prompt run was not found"));
        run.succeed(sanitizedOutcome, completedAt);
        runs.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void fail(UUID runId, String errorCode, Instant completedAt) {
        PromptRun run = runs.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Prompt run was not found"));
        run.fail(errorCode, completedAt);
        runs.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    UUID recordEvaluation(
            CurrentActor actor,
            AssetConsumptionRelease release,
            int passedCases,
            int totalCases,
            String details,
            Instant evaluatedAt) {
        return evaluations.saveAndFlush(new PromptEvaluationRun(
                        actor.organizationId(),
                        release.assetId(),
                        release.releaseId(),
                        release.digest(),
                        actor.userId(),
                        passedCases,
                        totalCases,
                        details,
                        evaluatedAt))
                .getId();
    }
}
