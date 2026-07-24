package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.curation.CurationProvenance;
import com.orgmemory.graphrag.curation.GraphCurationFingerprint;
import com.orgmemory.graphrag.curation.GraphCurationOverlay;
import com.orgmemory.graphrag.curation.GraphCurationRecord;
import com.orgmemory.graphrag.curation.GraphCurationStore;
import com.orgmemory.graphrag.curation.GraphIdentityKind;
import com.orgmemory.graphrag.curation.GraphIdentityRef;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL append-only curation ledger with authorization-before-overlay reads. */
public final class PostgresGraphCurationStore implements GraphCurationStore {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public PostgresGraphCurationStore(
            NamedParameterJdbcTemplate jdbc,
            PlatformTransactionManager transactionManager) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.transactions = new TransactionTemplate(
                Objects.requireNonNull(transactionManager, "transactionManager"));
    }

    @Override
    public GraphCurationRecord append(
            String idempotencyKey, GraphCurationRecord record) {
        String normalizedKey = requireText(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(record, "record");
        return transactions.execute(status -> {
            String fingerprint = GraphCurationFingerprint.fingerprint(record);
            GraphCurationRecord existing =
                    existing(record.namespace(), normalizedKey, fingerprint);
            if (existing != null) {
                return existing;
            }
            validateIdentities(record);
            insert(normalizedKey, fingerprint, record);
            return record;
        });
    }

    @Override
    public void deactivate(
            ProjectionNamespace namespace,
            UUID recordId,
            CurationProvenance provenance) {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(recordId, "recordId");
        Objects.requireNonNull(provenance, "provenance");
        int changed = jdbc.update("""
                UPDATE graph_curation_records
                SET active = false,
                    deactivated_by_user_id = :actorUserId,
                    deactivated_at = :deactivatedAt,
                    deactivation_reason = :reason
                WHERE id = :id
                  AND organization_id = :organizationId
                  AND workspace = :workspace
                  AND collection_name = :collection
                  AND active
                """,
                namespaceParameters(namespace)
                        .addValue("id", recordId)
                        .addValue("actorUserId", provenance.actorUserId())
                        .addValue(
                                "deactivatedAt",
                                Timestamp.from(provenance.curatedAt()))
                        .addValue("reason", provenance.reason()));
        if (changed == 0 && !exists(namespace, recordId)) {
            throw new IllegalArgumentException("curation record was not found");
        }
    }

    @Override
    public List<GraphCurationRecord> active(
            AuthorizedEvidenceScope scope, ProjectionNamespace namespace) {
        GraphCurationOverlay.requireMatchingScope(scope, namespace);
        if (scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource parameters =
                PostgresAuthorizedGraphSql.scopeParameters(scope)
                        .addValue("workspace", namespace.workspace())
                        .addValue("collection", namespace.collection());
        return jdbc.query("""
                WITH %s,
                     %s,
                     %s,
                visible_entities AS (
                    SELECT DISTINCT entity_id
                    FROM visible_entity_contributions
                ),
                visible_relations AS (
                    SELECT DISTINCT relation_id
                    FROM visible_relation_contributions
                )
                SELECT record.*
                FROM graph_curation_records record
                WHERE record.organization_id = :organizationId
                  AND record.workspace = :workspace
                  AND record.collection_name = :collection
                  AND record.active
                  AND (
                    (
                        record.curation_kind IN (
                            'CURATED_ENTITY', 'CURATED_RELATION'
                        )
                        AND record.governing_knowledge_asset_id
                                IN (:authorizedAssetIds)
                        AND EXISTS (
                            SELECT 1
                            FROM visible_knowledge_chunks chunk
                            WHERE chunk.source_revision_id =
                                    record.governing_source_revision_id
                              AND chunk.organization_id =
                                    record.organization_id
                              AND chunk.knowledge_asset_id =
                                    record.governing_knowledge_asset_id
                              AND (
                                  record.governing_chunk_id IS NULL
                                  OR chunk.id =
                                        record.governing_chunk_id
                              )
                              AND chunk.source_acl_snapshot_id =
                                    record.governing_acl_snapshot_id
                              AND chunk.ingestion_acl_generation =
                                    record.governing_acl_generation
                        )
                    )
                    OR (
                        record.curation_kind = 'IDENTITY_SUPPRESSION'
                        AND (
                            (
                                record.identity_kind = 'ENTITY'
                                AND record.identity_id IN (
                                    SELECT entity_id FROM visible_entities
                                )
                            )
                            OR (
                                record.identity_kind = 'RELATION'
                                AND record.identity_id IN (
                                    SELECT relation_id FROM visible_relations
                                )
                            )
                        )
                    )
                    OR (
                        record.curation_kind = 'IDENTITY_ALIAS'
                        AND (
                            (
                                record.identity_kind = 'ENTITY'
                                AND record.identity_id IN (
                                    SELECT entity_id FROM visible_entities
                                )
                                AND record.target_identity_id IN (
                                    SELECT entity_id FROM visible_entities
                                )
                            )
                            OR (
                                record.identity_kind = 'RELATION'
                                AND record.identity_id IN (
                                    SELECT relation_id FROM visible_relations
                                )
                                AND record.target_identity_id IN (
                                    SELECT relation_id FROM visible_relations
                                )
                            )
                        )
                    )
                  )
                ORDER BY record.curated_at, record.id
                """.formatted(
                        PostgresAuthorizedGraphSql.VISIBLE_KNOWLEDGE_CHUNKS,
                        PostgresAuthorizedGraphSql.VISIBLE_ENTITY_CONTRIBUTIONS,
                        PostgresAuthorizedGraphSql.VISIBLE_RELATION_CONTRIBUTIONS),
                parameters,
                (resultSet, rowNumber) -> map(resultSet));
    }

    private GraphCurationRecord existing(
            ProjectionNamespace namespace,
            String idempotencyKey,
            String expectedFingerprint) {
        List<StoredCuration> stored = jdbc.query("""
                SELECT record.*, content_fingerprint
                FROM graph_curation_records record
                WHERE organization_id = :organizationId
                  AND workspace = :workspace
                  AND collection_name = :collection
                  AND idempotency_key = :idempotencyKey
                FOR UPDATE
                """,
                namespaceParameters(namespace)
                        .addValue("idempotencyKey", idempotencyKey),
                (resultSet, rowNumber) -> new StoredCuration(
                        resultSet.getString("content_fingerprint"),
                        map(resultSet)));
        if (stored.isEmpty()) {
            return null;
        }
        StoredCuration existing = stored.getFirst();
        if (!existing.fingerprint().equals(expectedFingerprint)) {
            throw new CurationConflictException(
                    "curation idempotency key was reused with different content");
        }
        return existing.record();
    }

    private void insert(
            String idempotencyKey,
            String fingerprint,
            GraphCurationRecord record) {
        MapSqlParameterSource parameters = parameters(record)
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("contentFingerprint", fingerprint);
        jdbc.update("""
                INSERT INTO graph_curation_records (
                    id,
                    organization_id,
                    workspace,
                    collection_name,
                    curation_kind,
                    identity_kind,
                    identity_id,
                    target_identity_id,
                    source_entity_id,
                    target_entity_id,
                    identity_name,
                    contribution_type,
                    keywords,
                    description,
                    weight,
                    governing_knowledge_asset_id,
                    governing_source_revision_id,
                    governing_chunk_id,
                    governing_acl_snapshot_id,
                    governing_acl_generation,
                    actor_user_id,
                    authorization_model_id,
                    curation_acl_generation,
                    curated_at,
                    reason,
                    idempotency_key,
                    content_fingerprint
                )
                VALUES (
                    :id,
                    :organizationId,
                    :workspace,
                    :collection,
                    :curationKind,
                    :identityKind,
                    :identityId,
                    :targetIdentityId,
                    :sourceEntityId,
                    :targetEntityId,
                    :identityName,
                    :contributionType,
                    :keywords,
                    :description,
                    :weight,
                    :governingKnowledgeAssetId,
                    :governingSourceRevisionId,
                    :governingChunkId,
                    :governingAclSnapshotId,
                    :governingAclGeneration,
                    :actorUserId,
                    :authorizationModelId,
                    :curationAclGeneration,
                    :curatedAt,
                    :reason,
                    :idempotencyKey,
                    :contentFingerprint
                )
                """, parameters);
    }

    private void validateIdentities(GraphCurationRecord record) {
        switch (record) {
            case GraphCurationRecord.CuratedEntity ignored -> {
                // Creating a new governed identity is valid.
            }
            case GraphCurationRecord.CuratedRelation relation -> {
                requireIdentityExists(
                        record.namespace(), relation.sourceEntity());
                requireIdentityExists(
                        record.namespace(), relation.targetEntity());
            }
            case GraphCurationRecord.IdentityAlias alias -> {
                requireIdentityExists(record.namespace(), alias.source());
                requireIdentityExists(record.namespace(), alias.target());
            }
            case GraphCurationRecord.IdentitySuppression suppression ->
                    requireIdentityExists(
                            record.namespace(), suppression.identity());
        }
    }

    private void requireIdentityExists(
            ProjectionNamespace namespace, GraphIdentityRef identity) {
        String canonicalTable =
                identity.kind() == GraphIdentityKind.ENTITY
                        ? "graph_entities"
                        : "graph_relations";
        String curatedKind =
                identity.kind() == GraphIdentityKind.ENTITY
                        ? "CURATED_ENTITY"
                        : "CURATED_RELATION";
        MapSqlParameterSource parameters = namespaceParameters(namespace)
                .addValue("identityId", identity.id())
                .addValue("curatedKind", curatedKind);
        Boolean exists = jdbc.queryForObject("""
                SELECT (
                    EXISTS (
                        SELECT 1
                        FROM %s
                        WHERE organization_id = :organizationId
                          AND id = :identityId
                    )
                    OR EXISTS (
                        SELECT 1
                        FROM graph_curation_records
                        WHERE organization_id = :organizationId
                          AND workspace = :workspace
                          AND collection_name = :collection
                          AND curation_kind = :curatedKind
                          AND identity_id = :identityId
                          AND active
                    )
                )
                """.formatted(canonicalTable),
                parameters,
                Boolean.class);
        if (!Boolean.TRUE.equals(exists)) {
            throw new IllegalArgumentException(
                    identity.kind() + " identity was not found");
        }
    }

    private boolean exists(ProjectionNamespace namespace, UUID recordId) {
        Boolean exists = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM graph_curation_records
                    WHERE id = :id
                      AND organization_id = :organizationId
                      AND workspace = :workspace
                      AND collection_name = :collection
                )
                """,
                namespaceParameters(namespace).addValue("id", recordId),
                Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    private static MapSqlParameterSource parameters(GraphCurationRecord record) {
        MapSqlParameterSource parameters = namespaceParameters(record.namespace())
                .addValue("id", record.id())
                .addValue("actorUserId", record.provenance().actorUserId())
                .addValue(
                        "authorizationModelId",
                        record.provenance().authorizationModelId())
                .addValue(
                        "curationAclGeneration",
                        record.provenance().aclGeneration())
                .addValue(
                        "curatedAt",
                        Timestamp.from(record.provenance().curatedAt()))
                .addValue("reason", record.provenance().reason())
                .addValue("identityKind", null)
                .addValue("identityId", null)
                .addValue("targetIdentityId", null)
                .addValue("sourceEntityId", null)
                .addValue("targetEntityId", null)
                .addValue("identityName", null)
                .addValue("contributionType", null)
                .addValue("keywords", null)
                .addValue("description", null)
                .addValue("weight", null)
                .addValue("governingKnowledgeAssetId", null)
                .addValue("governingSourceRevisionId", null)
                .addValue("governingChunkId", null)
                .addValue("governingAclSnapshotId", null)
                .addValue("governingAclGeneration", null);
        switch (record) {
            case GraphCurationRecord.CuratedEntity entity -> {
                parameters.addValue("curationKind", "CURATED_ENTITY");
                parameters.addValue("identityKind", "ENTITY");
                parameters.addValue("identityId", entity.entity().id());
                parameters.addValue("identityName", entity.name());
                parameters.addValue("contributionType", entity.type());
                parameters.addValue("description", entity.description());
                addEvidence(parameters, entity.governingEvidence());
            }
            case GraphCurationRecord.CuratedRelation relation -> {
                parameters.addValue("curationKind", "CURATED_RELATION");
                parameters.addValue("identityKind", "RELATION");
                parameters.addValue("identityId", relation.relation().id());
                parameters.addValue(
                        "sourceEntityId", relation.sourceEntity().id());
                parameters.addValue(
                        "targetEntityId", relation.targetEntity().id());
                parameters.addValue("contributionType", relation.type());
                parameters.addValue(
                        "keywords", encodeKeywords(relation.keywords()));
                parameters.addValue("description", relation.description());
                parameters.addValue("weight", relation.weight());
                addEvidence(parameters, relation.governingEvidence());
            }
            case GraphCurationRecord.IdentityAlias alias -> {
                parameters.addValue("curationKind", "IDENTITY_ALIAS");
                parameters.addValue("identityKind", alias.source().kind().name());
                parameters.addValue("identityId", alias.source().id());
                parameters.addValue("targetIdentityId", alias.target().id());
            }
            case GraphCurationRecord.IdentitySuppression suppression -> {
                parameters.addValue("curationKind", "IDENTITY_SUPPRESSION");
                parameters.addValue(
                        "identityKind", suppression.identity().kind().name());
                parameters.addValue("identityId", suppression.identity().id());
            }
        }
        return parameters;
    }

    private static void addEvidence(
            MapSqlParameterSource parameters, EvidenceReference evidence) {
        parameters.addValue(
                "governingKnowledgeAssetId", evidence.knowledgeAssetId());
        parameters.addValue(
                "governingSourceRevisionId", evidence.sourceRevisionId());
        parameters.addValue("governingChunkId", evidence.chunkId());
        parameters.addValue(
                "governingAclSnapshotId", evidence.aclSnapshotId());
        parameters.addValue(
                "governingAclGeneration", evidence.aclGeneration());
    }

    private static GraphCurationRecord map(ResultSet resultSet)
            throws SQLException {
        ProjectionNamespace namespace = new ProjectionNamespace(
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getString("workspace"),
                resultSet.getString("collection_name"));
        CurationProvenance provenance = new CurationProvenance(
                resultSet.getObject("actor_user_id", UUID.class),
                resultSet.getString("authorization_model_id"),
                resultSet.getLong("curation_acl_generation"),
                resultSet.getTimestamp("curated_at").toInstant(),
                resultSet.getString("reason"));
        UUID id = resultSet.getObject("id", UUID.class);
        GraphIdentityKind identityKind =
                GraphIdentityKind.valueOf(resultSet.getString("identity_kind"));
        GraphIdentityRef identity = new GraphIdentityRef(
                identityKind, resultSet.getObject("identity_id", UUID.class));
        return switch (resultSet.getString("curation_kind")) {
            case "CURATED_ENTITY" -> new GraphCurationRecord.CuratedEntity(
                    id,
                    namespace,
                    identity,
                    resultSet.getString("identity_name"),
                    resultSet.getString("contribution_type"),
                    resultSet.getString("description"),
                    evidence(resultSet),
                    provenance);
            case "CURATED_RELATION" -> new GraphCurationRecord.CuratedRelation(
                    id,
                    namespace,
                    identity,
                    GraphIdentityRef.entity(
                            resultSet.getObject("source_entity_id", UUID.class)),
                    GraphIdentityRef.entity(
                            resultSet.getObject("target_entity_id", UUID.class)),
                    resultSet.getString("contribution_type"),
                    decodeKeywords(resultSet.getString("keywords")),
                    resultSet.getString("description"),
                    resultSet.getDouble("weight"),
                    evidence(resultSet),
                    provenance);
            case "IDENTITY_ALIAS" -> new GraphCurationRecord.IdentityAlias(
                    id,
                    namespace,
                    identity,
                    new GraphIdentityRef(
                            identityKind,
                            resultSet.getObject(
                                    "target_identity_id", UUID.class)),
                    provenance);
            case "IDENTITY_SUPPRESSION" ->
                    new GraphCurationRecord.IdentitySuppression(
                            id, namespace, identity, provenance);
            default -> throw new IllegalStateException(
                    "unsupported graph curation kind");
        };
    }

    private static EvidenceReference evidence(ResultSet resultSet)
            throws SQLException {
        return new EvidenceReference(
                resultSet.getObject("organization_id", UUID.class),
                resultSet.getObject(
                        "governing_knowledge_asset_id", UUID.class),
                resultSet.getObject(
                        "governing_source_revision_id", UUID.class),
                resultSet.getObject("governing_chunk_id", UUID.class),
                resultSet.getObject("governing_acl_snapshot_id", UUID.class),
                resultSet.getLong("governing_acl_generation"));
    }

    private static String encodeKeywords(List<String> keywords) {
        StringBuilder encoded = new StringBuilder();
        for (String keyword : keywords) {
            encoded.append(keyword.length()).append(':').append(keyword);
        }
        return encoded.toString();
    }

    private static List<String> decodeKeywords(String encoded) {
        List<String> values = new ArrayList<>();
        int offset = 0;
        while (offset < encoded.length()) {
            int separator = encoded.indexOf(':', offset);
            if (separator < 0) {
                throw new IllegalStateException("invalid keyword encoding");
            }
            int length = Integer.parseInt(encoded.substring(offset, separator));
            int start = separator + 1;
            int end = start + length;
            if (length < 0 || end > encoded.length()) {
                throw new IllegalStateException("invalid keyword encoding");
            }
            values.add(encoded.substring(start, end));
            offset = end;
        }
        return List.copyOf(values);
    }

    private static MapSqlParameterSource namespaceParameters(
            ProjectionNamespace namespace) {
        Objects.requireNonNull(namespace, "namespace");
        return new MapSqlParameterSource()
                .addValue("organizationId", namespace.organizationId())
                .addValue("workspace", namespace.workspace())
                .addValue("collection", namespace.collection());
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private record StoredCuration(
            String fingerprint, GraphCurationRecord record) {
    }
}
