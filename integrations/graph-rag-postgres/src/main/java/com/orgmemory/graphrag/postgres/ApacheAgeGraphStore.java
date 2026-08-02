package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.storage.AuthorizedGraphTraversalSource.IncidentRelationPage;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionDiscardPermit;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AGE-selected graph store: relational evidence plus AGE-owned snapshot topology.
 *
 * <p>AGE never owns authorization, evidence, publication heads, or final traversal
 * semantics. It supplies only the exact incident-relation page selected here.
 */
public final class ApacheAgeGraphStore implements GraphStore {

    private final PostgresGraphStore relational;
    private final ApacheAgeBatchTopology topology;
    private final TransactionTemplate transactions;

    public ApacheAgeGraphStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresProjectionPublicationStore publications,
            int batchSize) {
        Objects.requireNonNull(transactionManager, "transactionManager");
        this.relational = new PostgresGraphStore(
                jdbc, transactionManager, publications, batchSize);
        this.topology = new ApacheAgeBatchTopology(jdbc, batchSize);
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public void stageReplaceRevision(
            ProjectionBatch batch,
            GraphRevisionContributions contributions) {
        prepareGraph(batch);
        transactions.executeWithoutResult(ignored -> {
            relational.stageReplaceRevision(batch, contributions);
            topology.rebuild(batch);
        });
    }

    @Override
    public void stageDeleteRevision(
            ProjectionBatch batch,
            UUID sourceRevisionId) {
        prepareGraph(batch);
        transactions.executeWithoutResult(ignored -> {
            relational.stageDeleteRevision(batch, sourceRevisionId);
            topology.rebuild(batch);
        });
    }

    @Override
    public void stageDeleteAsset(
            ProjectionBatch batch,
            UUID knowledgeAssetId) {
        prepareGraph(batch);
        transactions.executeWithoutResult(ignored -> {
            relational.stageDeleteAsset(batch, knowledgeAssetId);
            topology.rebuild(batch);
        });
    }

    @Override
    public void validateSnapshot(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot) {
        withReady(scope, snapshot, () -> null);
    }

    @Override
    public List<CanonicalEntity> loadEntities(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        return withReady(
                scope,
                snapshot,
                () -> relational.loadEntities(scope, snapshot, entityIds));
    }

    @Override
    public List<CanonicalRelation> loadRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        return withReady(
                scope,
                snapshot,
                () -> relational.loadRelations(scope, snapshot, relationIds));
    }

    @Override
    public List<EntityContribution> loadEntityContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        return withReady(
                scope,
                snapshot,
                () -> relational.loadEntityContributions(scope, snapshot, entityIds));
    }

    @Override
    public List<RelationContribution> loadRelationContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        return withReady(
                scope,
                snapshot,
                () -> relational.loadRelationContributions(scope, snapshot, relationIds));
    }

    @Override
    public List<CanonicalRelation> loadIncidentRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            int limit) {
        if (limit < 0 || limit > 10_000) {
            throw new IllegalArgumentException("limit must be between 0 and 10000");
        }
        if (limit == 0) {
            validateSnapshot(scope, snapshot);
            return List.of();
        }
        return withReady(
                scope,
                snapshot,
                () -> topology.loadIncidentRelationPage(
                                snapshot,
                                entityIds,
                                scope.authorizedAssetIds(),
                                null,
                                limit)
                        .relations());
    }

    @Override
    public IncidentRelationPage loadIncidentRelationPage(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            UUID afterRelationId,
            int pageSize) {
        return withReady(
                scope,
                snapshot,
                () -> topology.loadIncidentRelationPage(
                        snapshot,
                        entityIds,
                        scope.authorizedAssetIds(),
                        afterRelationId,
                        pageSize));
    }

    @Override
    public Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        return withReady(
                scope,
                snapshot,
                () -> relational.loadVisibleEntityDegrees(scope, snapshot, entityIds));
    }

    @Override
    public Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        return withReady(
                scope,
                snapshot,
                () -> relational.loadVisibleRelationWeights(scope, snapshot, relationIds));
    }

    @Override
    public void discard(
            ProjectionBatch batch,
            ProjectionDiscardPermit permit) {
        Objects.requireNonNull(permit, "permit").requireAuthorizes(batch);
        transactions.executeWithoutResult(ignored -> {
            topology.discard(batch);
            relational.discard(batch, permit);
        });
    }

    private <T> T withReady(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Supplier<T> operation) {
        return transactions.execute(status -> {
            relational.validateSnapshot(scope, snapshot);
            topology.requireReady(snapshot);
            return operation.get();
        });
    }

    private void prepareGraph(ProjectionBatch batch) {
        Objects.requireNonNull(batch, "batch");
        transactions.executeWithoutResult(ignored ->
                topology.prepareGraph(batch.namespace().organizationId()));
    }
}
