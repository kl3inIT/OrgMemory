package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

final class PostgresProjectionSupport {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PostgresProjectionPublicationStore publications;

    PostgresProjectionSupport(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            PostgresProjectionPublicationStore publications) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions =
                new TransactionTemplate(
                        Objects.requireNonNull(transactionManager, "transactionManager"));
        this.publications = Objects.requireNonNull(publications, "publications");
    }

    void stage(
            ProjectionBatch batch,
            ProjectionKind kind,
            String copySql,
            Runnable mutations) {
        stage(batch, kind, List.of(copySql), mutations);
    }

    void stage(
            ProjectionBatch batch,
            ProjectionKind kind,
            List<String> copySql,
            Runnable mutations) {
        List<String> copyStatements =
                List.copyOf(Objects.requireNonNull(copySql, "copySql"));
        Objects.requireNonNull(mutations, "mutations");
        transactions.executeWithoutResult(ignored -> {
            requireRequired(batch, kind);
            publications.ensureBatch(batch);
            int initialized = jdbc.update(
                    """
                    INSERT INTO projection_stage_initializations (
                        batch_id, projection_kind)
                    VALUES (:batchId, :projectionKind)
                    ON CONFLICT (batch_id, projection_kind) DO NOTHING
                    """,
                    Map.of(
                            "batchId", batch.id(),
                            "projectionKind", kind.name()));
            if (initialized == 1 && batch.expectedPreviousGeneration() > 0) {
                UUID predecessor = publications
                        .publishedBatchId(
                                batch.namespace(),
                                batch.expectedPreviousGeneration())
                        .orElseThrow(() -> new IllegalStateException(
                                "previous published snapshot is unavailable"));
                Map<String, Object> copyParameters = Map.of(
                        "batchId", batch.id(),
                        "predecessorBatchId", predecessor,
                        "generation", batch.generation());
                copyStatements.forEach(statement ->
                        jdbc.update(statement, copyParameters));
            }
            mutations.run();
        });
    }

    void discard(ProjectionBatch batch, ProjectionKind kind) {
        discard(batch, kind, List.of());
    }

    void discard(
            ProjectionBatch batch,
            ProjectionKind kind,
            List<String> deletionSql) {
        Objects.requireNonNull(batch, "batch");
        List<String> deletions =
                List.copyOf(Objects.requireNonNull(deletionSql, "deletionSql"));
        transactions.executeWithoutResult(ignored -> {
            Map<String, Object> parameters = Map.of(
                    "batchId", batch.id(),
                    "projectionKind", kind.name());
            deletions.forEach(sql -> jdbc.update(sql, parameters));
            jdbc.update(
                    """
                    DELETE FROM projection_stage_initializations
                    WHERE batch_id = :batchId AND projection_kind = :projectionKind
                    """,
                    parameters);
        });
    }

    void requireReadable(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            ProjectionKind kind) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!scope.organizationId().equals(snapshot.namespace().organizationId())) {
            throw new IllegalArgumentException(
                    "authorization scope and snapshot must share an organization");
        }
        publications.requireReadable(snapshot, kind);
    }

    static MapSqlParameterSource evidenceParameters(EvidenceReference evidence) {
        return new MapSqlParameterSource()
                .addValue("organizationId", evidence.organizationId())
                .addValue("knowledgeAssetId", evidence.knowledgeAssetId())
                .addValue("sourceRevisionId", evidence.sourceRevisionId())
                .addValue("chunkId", evidence.chunkId())
                .addValue("aclSnapshotId", evidence.aclSnapshotId())
                .addValue("aclGeneration", evidence.aclGeneration());
    }

    static EvidenceReference evidence(java.sql.ResultSet resultSet)
            throws java.sql.SQLException {
        return new EvidenceReference(
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getObject("knowledge_asset_id", UUID.class),
                resultSet.getObject("source_revision_id", UUID.class),
                resultSet.getObject("chunk_id", UUID.class),
                resultSet.getObject("acl_snapshot_id", UUID.class),
                resultSet.getLong("acl_generation"));
    }

    static MapSqlParameterSource visibilityParameters(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot) {
        return new MapSqlParameterSource()
                .addValue("batchId", snapshot.batchId())
                .addValue("organizationId", scope.organizationId())
                .addValue("authorizedAssetIds", scope.authorizedAssetIds());
    }

    static boolean noAuthorizedAssets(AuthorizedEvidenceScope scope) {
        return scope.authorizedAssetIds().isEmpty();
    }

    static void requireSameOrganization(
            ProjectionBatch batch,
            EvidenceReference evidence) {
        if (!batch.namespace().organizationId().equals(evidence.organizationId())) {
            throw new IllegalArgumentException(
                    "projection record belongs to another organization");
        }
    }

    static void requireIds(Collection<String> ids) {
        Objects.requireNonNull(ids, "ids");
        if (ids.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("ids must contain only non-blank values");
        }
    }

    private static void requireRequired(
            ProjectionBatch batch,
            ProjectionKind kind) {
        Objects.requireNonNull(batch, "batch");
        if (!batch.requiredProjections().contains(kind)) {
            throw new IllegalArgumentException(
                    "batch does not require projection " + kind);
        }
    }
}
