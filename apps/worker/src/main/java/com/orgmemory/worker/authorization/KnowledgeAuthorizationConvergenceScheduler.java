package com.orgmemory.worker.authorization;

import com.orgmemory.core.knowledge.KnowledgeAuthorizationConvergenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs safely on every worker replica. OpenFGA writes ignore duplicates, deletes ignore
 * missing tuples, and PostgreSQL model recording uses a pessimistic row lock, so concurrent
 * sweeps converge on the same state without requiring a separate leader-election service.
 */
@Component
@ConditionalOnProperty(
        prefix = "orgmemory.authorization.convergence",
        name = "scheduling-enabled",
        havingValue = "true",
        matchIfMissing = true)
class KnowledgeAuthorizationConvergenceScheduler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(KnowledgeAuthorizationConvergenceScheduler.class);

    private final KnowledgeAuthorizationConvergenceService convergence;
    private final KnowledgeAuthorizationConvergenceProperties properties;

    KnowledgeAuthorizationConvergenceScheduler(
            KnowledgeAuthorizationConvergenceService convergence,
            KnowledgeAuthorizationConvergenceProperties properties) {
        this.convergence = convergence;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${orgmemory.authorization.convergence.poll-interval:5m}")
    void reconcile() {
        var report = convergence.reconcile(
                properties.modelBatchSize(),
                properties.tuplePageSize(),
                properties.maximumTuplePages());
        if (!report.complete()) {
            LOGGER.warn(
                    "Knowledge authorization convergence incomplete: reason={}, modelDrift={}/{}, "
                            + "tuplesScanned={}, orphanTuplesDeleted={}",
                    report.reasonCode(),
                    report.modelDriftRepaired(),
                    report.modelDriftDetected(),
                    report.tuplesScanned(),
                    report.orphanTuplesDeleted());
        } else if (report.modelDriftRepaired() > 0 || report.orphanTuplesDeleted() > 0) {
            LOGGER.info(
                    "Knowledge authorization convergence repaired modelDrift={} and orphanTuples={}",
                    report.modelDriftRepaired(),
                    report.orphanTuplesDeleted());
        }
    }
}
