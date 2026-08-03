package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.storage.ProjectionKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded, operator-invoked repair of retained relational publications into AGE. */
final class ApacheAgePublishedBatchReconciler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ApacheAgePublishedBatchReconciler.class);

    private final PostgresProjectionPublicationStore publications;
    private final ApacheAgeBatchTopology topology;
    private final TransactionTemplate transactions;

    ApacheAgePublishedBatchReconciler(
            PostgresProjectionPublicationStore publications,
            ApacheAgeBatchTopology topology,
            PlatformTransactionManager transactionManager) {
        this.publications = Objects.requireNonNull(publications, "publications");
        this.topology = Objects.requireNonNull(topology, "topology");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    Result reconcile(PostgresGraphRagProperties properties) {
        Objects.requireNonNull(properties, "properties");
        if (properties.getTopologyBackend() != PostgresGraphTopologyBackend.APACHE_AGE) {
            throw new IllegalStateException(
                    "Published-batch reconciliation requires topology-backend=APACHE_AGE");
        }

        List<PublishedGraphBatch> candidates = preflight(properties);
        int repaired = 0;
        int skipped = 0;
        for (PublishedGraphBatch candidate : candidates) {
            publications.requireReadable(candidate.snapshot(), ProjectionKind.GRAPH);
            ApacheAgeBatchTopology.MarkerInspection inspection = Objects.requireNonNull(
                    transactions.execute(ignored ->
                            topology.inspectReadyMarker(candidate.snapshot())));
            ApacheAgeBatchTopology.MarkerState state = inspection.state();
            if (state == ApacheAgeBatchTopology.MarkerState.READY_EXACT) {
                skipped++;
                continue;
            }
            if (state == ApacheAgeBatchTopology.MarkerState.AGE_UNAVAILABLE) {
                throw new IllegalStateException(
                        "Apache AGE became unavailable while reconciling published batch "
                                + candidate.batch().id(),
                        inspection.failure());
            }

            transactions.executeWithoutResult(ignored -> topology.prepareGraph(
                    candidate.batch().namespace().organizationId()));
            transactions.executeWithoutResult(ignored -> {
                topology.rebuild(candidate.batch());
                topology.requireReady(candidate.snapshot());
            });
            repaired++;
            LOGGER.info(
                    "Reconciled Apache AGE published batch batchId={} generation={} previousMarkerState={}",
                    candidate.batch().id(),
                    candidate.batch().generation(),
                    state);
        }
        Result result = new Result(candidates.size(), repaired, skipped);
        LOGGER.info("Apache AGE published-batch reconciliation completed result={}", result);
        return result;
    }

    private List<PublishedGraphBatch> preflight(PostgresGraphRagProperties properties) {
        List<PublishedGraphBatch> candidates = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        Instant afterPublishedAt = null;
        UUID afterBatchId = null;
        long totalEntities = 0;
        long totalRelations = 0;

        while (true) {
            List<PublishedGraphBatch> page = publications.publishedGraphBatchPage(
                    afterPublishedAt,
                    afterBatchId,
                    properties.getReconciliationPageSize());
            if (page.isEmpty()) {
                return List.copyOf(candidates);
            }
            for (PublishedGraphBatch candidate : page) {
                if (!seen.add(candidate.batch().id())) {
                    throw new IllegalStateException(
                            "Published graph batch enumeration duplicated batch "
                                    + candidate.batch().id());
                }
                candidates.add(candidate);
                if (candidates.size() > properties.getReconciliationMaximumBatches()) {
                    throw limitExceeded(
                            candidate,
                            "batches",
                            candidates.size(),
                            properties.getReconciliationMaximumBatches());
                }
                totalEntities = addMeasured(
                        totalEntities, candidate.entityCount(), candidate, "entities");
                if (totalEntities > properties.getReconciliationMaximumEntities()) {
                    throw limitExceeded(
                            candidate,
                            "entities",
                            totalEntities,
                            properties.getReconciliationMaximumEntities());
                }
                totalRelations = addMeasured(
                        totalRelations,
                        candidate.relationContributionCount(),
                        candidate,
                        "relationContributions");
                if (totalRelations
                        > properties.getReconciliationMaximumRelationContributions()) {
                    throw limitExceeded(
                            candidate,
                            "relationContributions",
                            totalRelations,
                            properties.getReconciliationMaximumRelationContributions());
                }
                if (candidate.unresolvedRelationCount() != 0
                        || candidate.unresolvedEndpointCount() != 0) {
                    throw new IllegalStateException(
                            "Relational graph topology is incomplete for published batch "
                                    + candidate.batch().id()
                                    + ": unresolvedRelations="
                                    + candidate.unresolvedRelationCount()
                                    + ", unresolvedEndpoints="
                                    + candidate.unresolvedEndpointCount());
                }
            }
            PublishedGraphBatch last = page.getLast();
            afterPublishedAt = last.snapshot().publishedAt();
            afterBatchId = last.batch().id();
            if (page.size() < properties.getReconciliationPageSize()) {
                return List.copyOf(candidates);
            }
        }
    }

    private static long addMeasured(
            long current,
            long value,
            PublishedGraphBatch candidate,
            String measurement) {
        try {
            return Math.addExact(current, value);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Published graph reconciliation "
                            + measurement
                            + " overflow at batch "
                            + candidate.batch().id(),
                    exception);
        }
    }

    private static IllegalStateException limitExceeded(
            PublishedGraphBatch candidate,
            String measurement,
            long measured,
            long maximum) {
        return new IllegalStateException(
                "Published graph reconciliation limit exceeded at batch "
                        + candidate.batch().id()
                        + ": measurement="
                        + measurement
                        + ", measured="
                        + measured
                        + ", maximum="
                        + maximum);
    }

    record Result(int candidates, int repaired, int skipped) {
        Result {
            if (candidates < 0 || repaired < 0 || skipped < 0
                    || repaired + skipped != candidates) {
                throw new IllegalArgumentException("invalid reconciliation result");
            }
        }
    }
}
