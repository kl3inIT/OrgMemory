package com.orgmemory.graphrag.neo4j;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.GraphRevisionContributions;
import com.orgmemory.graphrag.storage.AuthorizedGraphTraversalSource;
import com.orgmemory.graphrag.storage.AuthorizedGraphTraversalSource.IncidentRelationPage;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.neo4j.driver.Record;

public final class Neo4jGraphStore implements GraphStore {

    private static final String VISIBLE_ENTITY_CONTRIBUTION = """
            contribution.batchId = $batchId
            AND contribution.organizationId = $organizationId
            AND contribution.workspace = $workspace
            AND contribution.collection = $collection
            AND contribution.generation = $generation
            AND contribution.knowledgeAssetId IN $authorizedAssetIds
            """;

    private static final String VISIBLE_RELATION_CONTRIBUTION = """
            contribution.batchId = $batchId
            AND contribution.organizationId = $organizationId
            AND contribution.workspace = $workspace
            AND contribution.collection = $collection
            AND contribution.generation = $generation
            AND contribution.knowledgeAssetId IN $authorizedAssetIds
            """;

    private static final String VISIBLE_RELATION = """
            relation.batchId = $batchId
            AND relation.generation = $generation
            AND EXISTS {
                MATCH (relationContribution:OrgMemoryGraphRelationContribution)
                WHERE relationContribution.relationId = relation.relationId
                  AND relationContribution.batchId = $batchId
                  AND relationContribution.organizationId = $organizationId
                  AND relationContribution.workspace = $workspace
                  AND relationContribution.collection = $collection
                  AND relationContribution.generation = $generation
                  AND relationContribution.knowledgeAssetId IN $authorizedAssetIds
            }
            AND EXISTS {
                MATCH (sourceContribution:OrgMemoryGraphEntityContribution)
                      -[:CONTRIBUTES_TO]->(source)
                WHERE sourceContribution.batchId = $batchId
                  AND sourceContribution.organizationId = $organizationId
                  AND sourceContribution.workspace = $workspace
                  AND sourceContribution.collection = $collection
                  AND sourceContribution.generation = $generation
                  AND sourceContribution.knowledgeAssetId IN $authorizedAssetIds
            }
            AND EXISTS {
                MATCH (targetContribution:OrgMemoryGraphEntityContribution)
                      -[:CONTRIBUTES_TO]->(target)
                WHERE targetContribution.batchId = $batchId
                  AND targetContribution.organizationId = $organizationId
                  AND targetContribution.workspace = $workspace
                  AND targetContribution.collection = $collection
                  AND targetContribution.generation = $generation
                  AND targetContribution.knowledgeAssetId IN $authorizedAssetIds
            }
            """;

    private final Neo4jOperations operations;
    private final Neo4jProjectionSupport support;
    private final int writeBatchSize;

    public Neo4jGraphStore(
            Neo4jOperations operations,
            ProjectionPublicationStore publications,
            Neo4jGraphRagProperties properties) {
        this.operations = Objects.requireNonNull(operations, "operations");
        Objects.requireNonNull(properties, "properties");
        this.support = new Neo4jProjectionSupport(
                operations,
                publications,
                properties.getCopyPageSize(),
                properties.getCopyWaitTimeout(),
                properties.getCopyLease());
        this.writeBatchSize = properties.getWriteBatchSize();
    }

    @Override
    public void stageReplaceRevision(
            ProjectionBatch batch,
            GraphRevisionContributions contributions) {
        Objects.requireNonNull(contributions, "contributions");
        if (!batch.namespace().organizationId().equals(contributions.organizationId())) {
            throw new IllegalArgumentException(
                    "graph revision must belong to the batch organization");
        }
        support.stage(batch, () -> {
            deleteRevision(batch, contributions.sourceRevisionId());
            writeEntityContributions(batch, contributions.entities());
            writeRelationContributions(batch, contributions.relations());
            removeOrphans(batch);
        });
    }

    @Override
    public void stageDeleteRevision(
            ProjectionBatch batch,
            UUID sourceRevisionId) {
        Objects.requireNonNull(sourceRevisionId, "sourceRevisionId");
        support.stage(batch, () -> {
            deleteRevision(batch, sourceRevisionId);
            removeOrphans(batch);
        });
    }

    @Override
    public void stageDeleteAsset(
            ProjectionBatch batch,
            UUID knowledgeAssetId) {
        Objects.requireNonNull(knowledgeAssetId, "knowledgeAssetId");
        support.stage(batch, () -> {
            deleteAsset(batch, knowledgeAssetId);
            removeOrphans(batch);
        });
    }

    @Override
    public void validateSnapshot(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot) {
        support.requireReadable(scope, snapshot);
    }

    @Override
    public List<CanonicalEntity> loadEntities(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        List<UUID> ids = requireIds(entityIds);
        Map<String, Object> parameters = visibility(scope, snapshot, ids);
        if (ids.isEmpty() || scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        return operations.read(transaction -> transaction.run(
                        """
                        MATCH (entity:OrgMemoryGraphEntity)
                        WHERE entity.batchId = $batchId
                          AND entity.entityId IN $ids
                          AND EXISTS {
                              MATCH (contribution:OrgMemoryGraphEntityContribution)
                                    -[:CONTRIBUTES_TO]->(entity)
                              WHERE %s
                          }
                        RETURN entity.entityId AS entityId,
                               entity.normalizedName AS normalizedName
                        ORDER BY entity.entityId
                        """
                                .formatted(VISIBLE_ENTITY_CONTRIBUTION),
                        parameters)
                .list(Neo4jGraphStore::entity));
    }

    @Override
    public List<CanonicalRelation> loadRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        List<UUID> ids = requireIds(relationIds);
        Map<String, Object> parameters = visibility(scope, snapshot, ids);
        if (ids.isEmpty() || scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        return operations.read(transaction -> transaction.run(
                        """
                        MATCH (source:OrgMemoryGraphEntity)
                              -[relation:ORGMEMORY_RELATES]->
                              (target:OrgMemoryGraphEntity)
                        WHERE relation.relationId IN $ids
                          AND %s
                        RETURN relation.relationId AS relationId,
                               relation.sourceEntityId AS sourceEntityId,
                               relation.targetEntityId AS targetEntityId,
                               relation.orientation AS orientation
                        ORDER BY relation.relationId
                        """
                                .formatted(VISIBLE_RELATION),
                        parameters)
                .list(Neo4jGraphStore::relation));
    }

    @Override
    public List<EntityContribution> loadEntityContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        List<UUID> ids = requireIds(entityIds);
        Map<String, Object> parameters = visibility(scope, snapshot, ids);
        if (ids.isEmpty() || scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        return operations.read(transaction -> transaction.run(
                        """
                        MATCH (contribution:OrgMemoryGraphEntityContribution)
                              -[:CONTRIBUTES_TO]->
                              (entity:OrgMemoryGraphEntity)
                        WHERE contribution.entityId IN $ids
                          AND %s
                        RETURN properties(contribution) AS contribution,
                               entity.normalizedName AS normalizedName
                        ORDER BY contribution.contributionId
                        """
                                .formatted(VISIBLE_ENTITY_CONTRIBUTION),
                        parameters)
                .list(Neo4jGraphStore::entityContribution));
    }

    @Override
    public List<RelationContribution> loadRelationContributions(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        List<UUID> ids = requireIds(relationIds);
        Map<String, Object> parameters = visibility(scope, snapshot, ids);
        if (ids.isEmpty() || scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        return operations.read(transaction -> transaction.run(
                        """
                        MATCH (source:OrgMemoryGraphEntity)
                              -[relation:ORGMEMORY_RELATES]->
                              (target:OrgMemoryGraphEntity)
                        MATCH (contribution:OrgMemoryGraphRelationContribution)
                        WHERE contribution.relationId = relation.relationId
                          AND contribution.relationId IN $ids
                          AND %s
                          AND %s
                        RETURN properties(contribution) AS contribution,
                               relation.relationId AS relationId,
                               relation.sourceEntityId AS sourceEntityId,
                               relation.targetEntityId AS targetEntityId,
                               relation.orientation AS orientation
                        ORDER BY contribution.contributionId
                        """
                                .formatted(
                                        VISIBLE_RELATION_CONTRIBUTION,
                                        VISIBLE_RELATION),
                        parameters)
                .list(Neo4jGraphStore::relationContribution));
    }

    @Override
    public List<CanonicalRelation> loadIncidentRelations(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            int limit) {
        List<UUID> ids = requireIds(entityIds);
        requireNonNegative(limit, "limit");
        Map<String, Object> parameters = new LinkedHashMap<>(
                visibility(scope, snapshot, ids));
        parameters.put("limit", limit);
        if (ids.isEmpty() || limit == 0 || scope.authorizedAssetIds().isEmpty()) {
            return List.of();
        }
        return operations.read(transaction -> transaction.run(
                        """
                        MATCH (source:OrgMemoryGraphEntity)
                              -[relation:ORGMEMORY_RELATES]->
                              (target:OrgMemoryGraphEntity)
                        WHERE (source.entityId IN $ids OR target.entityId IN $ids)
                          AND %s
                        RETURN relation.relationId AS relationId,
                               relation.sourceEntityId AS sourceEntityId,
                               relation.targetEntityId AS targetEntityId,
                               relation.orientation AS orientation
                        ORDER BY relation.relationId
                        LIMIT $limit
                        """
                                .formatted(VISIBLE_RELATION),
                        parameters)
                .list(Neo4jGraphStore::relation));
    }

    @Override
    public IncidentRelationPage loadIncidentRelationPage(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds,
            UUID afterRelationId,
            int pageSize) {
        List<UUID> ids = requireIds(entityIds);
        requirePageSize(pageSize);
        validateSnapshot(scope, snapshot);
        if (ids.isEmpty() || scope.authorizedAssetIds().isEmpty()) {
            return new IncidentRelationPage(List.of(), null);
        }
        Map<String, Object> parameters = new LinkedHashMap<>(
                visibility(scope, snapshot, ids));
        parameters.put("fetchLimit", pageSize + 1);
        if (afterRelationId != null) {
            parameters.put("afterRelationId", afterRelationId.toString());
        }
        String afterPredicate = afterRelationId == null
                ? ""
                : "AND relation.relationId > $afterRelationId";
        List<CanonicalRelation> fetched = operations.read(transaction -> transaction.run(
                        """
                        MATCH (source:OrgMemoryGraphEntity)
                              -[relation:ORGMEMORY_RELATES]->
                              (target:OrgMemoryGraphEntity)
                        WHERE (source.entityId IN $ids OR target.entityId IN $ids)
                          %s
                          AND %s
                        RETURN relation.relationId AS relationId,
                               relation.sourceEntityId AS sourceEntityId,
                               relation.targetEntityId AS targetEntityId,
                               relation.orientation AS orientation
                        ORDER BY relation.relationId
                        LIMIT $fetchLimit
                        """
                                .formatted(afterPredicate, VISIBLE_RELATION),
                        parameters)
                .list(Neo4jGraphStore::relation));
        boolean hasMore = fetched.size() > pageSize;
        List<CanonicalRelation> page = hasMore
                ? List.copyOf(fetched.subList(0, pageSize))
                : List.copyOf(fetched);
        return new IncidentRelationPage(
                page,
                hasMore ? page.getLast().id() : null);
    }

    @Override
    public Map<UUID, Long> loadVisibleEntityDegrees(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> entityIds) {
        List<UUID> ids = requireIds(entityIds);
        Map<String, Object> parameters = visibility(scope, snapshot, ids);
        LinkedHashMap<UUID, Long> result = new LinkedHashMap<>();
        ids.stream().sorted().forEach(id -> result.put(id, 0L));
        if (ids.isEmpty() || scope.authorizedAssetIds().isEmpty()) {
            return Map.copyOf(result);
        }
        List<Record> records = operations.read(transaction -> transaction.run(
                        """
                        UNWIND $ids AS entityId
                        OPTIONAL MATCH (source:OrgMemoryGraphEntity)
                              -[relation:ORGMEMORY_RELATES]->
                              (target:OrgMemoryGraphEntity)
                        WHERE (source.entityId = entityId OR target.entityId = entityId)
                          AND %s
                        RETURN entityId, count(DISTINCT relation.relationId) AS degree
                        ORDER BY entityId
                        """
                                .formatted(VISIBLE_RELATION),
                        parameters)
                .list());
        records.forEach(record -> result.put(
                UUID.fromString(record.get("entityId").asString()),
                record.get("degree").asLong()));
        return Map.copyOf(result);
    }

    @Override
    public Map<UUID, Double> loadVisibleRelationWeights(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            Collection<UUID> relationIds) {
        List<UUID> ids = requireIds(relationIds);
        Map<String, Object> parameters = visibility(scope, snapshot, ids);
        LinkedHashMap<UUID, Double> result = new LinkedHashMap<>();
        ids.stream().sorted().forEach(id -> result.put(id, 0.0));
        if (ids.isEmpty() || scope.authorizedAssetIds().isEmpty()) {
            return Map.copyOf(result);
        }
        List<Record> records = operations.read(transaction -> transaction.run(
                        """
                        MATCH (source:OrgMemoryGraphEntity)
                              -[relation:ORGMEMORY_RELATES]->
                              (target:OrgMemoryGraphEntity)
                        MATCH (contribution:OrgMemoryGraphRelationContribution)
                        WHERE contribution.relationId = relation.relationId
                          AND contribution.relationId IN $ids
                          AND %s
                          AND %s
                        RETURN relation.relationId AS relationId,
                               sum(contribution.weight) AS weight
                        ORDER BY relation.relationId
                        """
                                .formatted(
                                        VISIBLE_RELATION_CONTRIBUTION,
                                        VISIBLE_RELATION),
                        parameters)
                .list());
        records.forEach(record -> result.put(
                UUID.fromString(record.get("relationId").asString()),
                record.get("weight").asDouble()));
        return Map.copyOf(result);
    }

    @Override
    public void discard(ProjectionBatch batch) {
        support.discard(batch, writeBatchSize);
    }

    private void writeEntityContributions(
            ProjectionBatch batch,
            List<EntityContribution> contributions) {
        for (List<EntityContribution> page : pages(contributions)) {
            List<Map<String, Object>> rows =
                    page.stream().map(item -> entityRow(batch, item)).toList();
            operations.writeWithoutResult(transaction -> transaction.run(
                            """
                            UNWIND $rows AS row
                            MERGE (entity:OrgMemoryGraphEntity {key: row.entityKey})
                            SET entity.batchId = row.batchId,
                                entity.entityId = row.entityId,
                                entity.normalizedName = row.normalizedName,
                                entity.generation = row.generation
                            MERGE (contribution:OrgMemoryGraphEntityContribution {
                                key: row.contributionKey
                            })
                            SET contribution = row.contribution
                            MERGE (contribution)-[:CONTRIBUTES_TO]->(entity)
                            """,
                            Map.of("rows", rows))
                    .consume());
        }
    }

    private void writeRelationContributions(
            ProjectionBatch batch,
            List<RelationContribution> contributions) {
        for (List<RelationContribution> page : pages(contributions)) {
            List<Map<String, Object>> rows =
                    page.stream().map(item -> relationRow(batch, item)).toList();
            operations.writeWithoutResult(transaction -> transaction.run(
                            """
                            UNWIND $rows AS row
                            MATCH (source:OrgMemoryGraphEntity {key: row.sourceKey})
                            MATCH (target:OrgMemoryGraphEntity {key: row.targetKey})
                            MERGE (source)-[relation:ORGMEMORY_RELATES {
                                key: row.relationKey
                            }]->(target)
                            SET relation.batchId = row.batchId,
                                relation.relationId = row.relationId,
                                relation.sourceEntityId = row.sourceEntityId,
                                relation.targetEntityId = row.targetEntityId,
                                relation.orientation = row.orientation,
                                relation.generation = row.generation
                            MERGE (contribution:OrgMemoryGraphRelationContribution {
                                key: row.contributionKey
                            })
                            SET contribution = row.contribution
                            """,
                            Map.of("rows", rows))
                    .consume());
        }
    }

    private void deleteRevision(ProjectionBatch batch, UUID sourceRevisionId) {
        operations.writeWithoutResult(transaction -> {
            Map<String, Object> parameters = Map.of(
                    "batchId", batch.id().toString(),
                    "sourceRevisionId", sourceRevisionId.toString());
            transaction.run(
                            """
                            MATCH (contribution:OrgMemoryGraphRelationContribution {
                                batchId: $batchId,
                                sourceRevisionId: $sourceRevisionId
                            })
                            DETACH DELETE contribution
                            """,
                            parameters)
                    .consume();
            transaction.run(
                            """
                            MATCH (contribution:OrgMemoryGraphEntityContribution {
                                batchId: $batchId,
                                sourceRevisionId: $sourceRevisionId
                            })
                            DETACH DELETE contribution
                            """,
                            parameters)
                    .consume();
        });
    }

    private void deleteAsset(ProjectionBatch batch, UUID knowledgeAssetId) {
        operations.writeWithoutResult(transaction -> {
            Map<String, Object> parameters = Map.of(
                    "batchId", batch.id().toString(),
                    "knowledgeAssetId", knowledgeAssetId.toString());
            transaction.run(
                            """
                            MATCH (contribution:OrgMemoryGraphRelationContribution {
                                batchId: $batchId,
                                knowledgeAssetId: $knowledgeAssetId
                            })
                            DETACH DELETE contribution
                            """,
                            parameters)
                    .consume();
            transaction.run(
                            """
                            MATCH (contribution:OrgMemoryGraphEntityContribution {
                                batchId: $batchId,
                                knowledgeAssetId: $knowledgeAssetId
                            })
                            DETACH DELETE contribution
                            """,
                            parameters)
                    .consume();
        });
    }

    private void removeOrphans(ProjectionBatch batch) {
        operations.writeWithoutResult(transaction -> {
            Map<String, Object> parameters = Map.of("batchId", batch.id().toString());
            transaction.run(
                            """
                            MATCH ()-[relation:ORGMEMORY_RELATES {
                                batchId: $batchId
                            }]->()
                            WHERE NOT EXISTS {
                                MATCH (contribution:OrgMemoryGraphRelationContribution {
                                    batchId: $batchId
                                })
                                WHERE contribution.relationId = relation.relationId
                            }
                            DELETE relation
                            """,
                            parameters)
                    .consume();
            transaction.run(
                            """
                            MATCH (entity:OrgMemoryGraphEntity {batchId: $batchId})
                            WHERE NOT EXISTS {
                                MATCH (contribution:OrgMemoryGraphEntityContribution)
                                      -[:CONTRIBUTES_TO]->(entity)
                                WHERE contribution.batchId = $batchId
                            }
                            DETACH DELETE entity
                            """,
                            parameters)
                    .consume();
        });
    }

    private Map<String, Object> visibility(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot,
            List<UUID> ids) {
        Map<String, Object> parameters =
                new LinkedHashMap<>(support.visibility(scope, snapshot));
        parameters.put("ids", ids.stream().map(UUID::toString).toList());
        return parameters;
    }

    private static CanonicalEntity entity(Record record) {
        return new CanonicalEntity(
                UUID.fromString(record.get("entityId").asString()),
                record.get("normalizedName").asString());
    }

    private static CanonicalRelation relation(Record record) {
        return new CanonicalRelation(
                UUID.fromString(record.get("relationId").asString()),
                UUID.fromString(record.get("sourceEntityId").asString()),
                UUID.fromString(record.get("targetEntityId").asString()),
                RelationOrientation.valueOf(record.get("orientation").asString()));
    }

    private static EntityContribution entityContribution(Record record) {
        Map<String, Object> properties = record.get("contribution").asMap();
        return new EntityContribution(
                uuid(properties, "contributionId"),
                new CanonicalEntity(
                        uuid(properties, "entityId"),
                        record.get("normalizedName").asString()),
                string(properties, "entityType"),
                string(properties, "description"),
                provenance(properties));
    }

    private static RelationContribution relationContribution(Record record) {
        Map<String, Object> properties = record.get("contribution").asMap();
        @SuppressWarnings("unchecked")
        List<String> keywords = List.copyOf(
                (List<String>) properties.get("keywords"));
        return new RelationContribution(
                uuid(properties, "contributionId"),
                relation(record),
                string(properties, "relationType"),
                keywords,
                string(properties, "description"),
                number(properties, "weight").doubleValue(),
                provenance(properties));
    }

    private static EvidenceProvenance provenance(Map<String, Object> properties) {
        Object chunk = properties.get("chunkId");
        return new EvidenceProvenance(
                new EvidenceReference(
                        uuid(properties, "organizationId"),
                        uuid(properties, "knowledgeAssetId"),
                        uuid(properties, "sourceRevisionId"),
                        chunk == null ? null : UUID.fromString(chunk.toString()),
                        uuid(properties, "aclSnapshotId"),
                        number(properties, "aclGeneration").longValue()),
                number(properties, "projectionGeneration").longValue(),
                string(properties, "extractorProvider"),
                string(properties, "extractorModel"),
                string(properties, "promptVersion"),
                string(properties, "extractionProfileFingerprint"),
                number(properties, "confidence").doubleValue(),
                Instant.parse(string(properties, "extractedAt")));
    }

    private static Map<String, Object> entityRow(
            ProjectionBatch batch,
            EntityContribution contribution) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("entityKey", key(batch.id(), contribution.entity().id()));
        row.put("contributionKey", key(batch.id(), contribution.id()));
        row.put("batchId", batch.id().toString());
        row.put("entityId", contribution.entity().id().toString());
        row.put("normalizedName", contribution.entity().normalizedName());
        row.put("generation", batch.generation());
        row.put(
                "contribution",
                contributionProperties(batch, contribution));
        return Map.copyOf(row);
    }

    private static Map<String, Object> relationRow(
            ProjectionBatch batch,
            RelationContribution contribution) {
        CanonicalRelation relation = contribution.relation();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sourceKey", key(batch.id(), relation.sourceEntityId()));
        row.put("targetKey", key(batch.id(), relation.targetEntityId()));
        row.put("relationKey", key(batch.id(), relation.id()));
        row.put("contributionKey", key(batch.id(), contribution.id()));
        row.put("batchId", batch.id().toString());
        row.put("relationId", relation.id().toString());
        row.put("sourceEntityId", relation.sourceEntityId().toString());
        row.put("targetEntityId", relation.targetEntityId().toString());
        row.put("orientation", relation.orientation().name());
        row.put("generation", batch.generation());
        Map<String, Object> properties = contributionProperties(batch, contribution);
        properties.put("relationType", contribution.type());
        properties.put("keywords", contribution.keywords());
        properties.put("description", contribution.description());
        properties.put("weight", contribution.weight());
        row.put("contribution", Map.copyOf(properties));
        return Map.copyOf(row);
    }

    private static Map<String, Object> contributionProperties(
            ProjectionBatch batch,
            EntityContribution contribution) {
        Map<String, Object> properties = provenanceProperties(batch, contribution.provenance());
        properties.put("key", key(batch.id(), contribution.id()));
        properties.put("contributionId", contribution.id().toString());
        properties.put("entityId", contribution.entity().id().toString());
        properties.put("entityType", contribution.type());
        properties.put("description", contribution.description());
        return properties;
    }

    private static Map<String, Object> contributionProperties(
            ProjectionBatch batch,
            RelationContribution contribution) {
        Map<String, Object> properties = provenanceProperties(batch, contribution.provenance());
        properties.put("key", key(batch.id(), contribution.id()));
        properties.put("contributionId", contribution.id().toString());
        properties.put("relationId", contribution.relation().id().toString());
        return properties;
    }

    private static Map<String, Object> provenanceProperties(
            ProjectionBatch batch,
            EvidenceProvenance provenance) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("batchId", batch.id().toString());
        properties.put(
                "organizationId",
                batch.namespace().organizationId().toString());
        properties.put("workspace", batch.namespace().workspace());
        properties.put("collection", batch.namespace().collection());
        properties.put("generation", batch.generation());
        properties.put(
                "knowledgeAssetId",
                provenance.knowledgeAssetId().toString());
        properties.put(
                "sourceRevisionId",
                provenance.sourceRevisionId().toString());
        if (provenance.chunkId() != null) {
            properties.put("chunkId", provenance.chunkId().toString());
        }
        properties.put("aclSnapshotId", provenance.aclSnapshotId().toString());
        properties.put("aclGeneration", provenance.aclGeneration());
        properties.put("projectionGeneration", provenance.projectionGeneration());
        properties.put("extractorProvider", provenance.extractorProvider());
        properties.put("extractorModel", provenance.extractorModel());
        properties.put("promptVersion", provenance.promptVersion());
        properties.put(
                "extractionProfileFingerprint",
                provenance.extractionProfileFingerprint());
        properties.put("confidence", provenance.confidence());
        properties.put("extractedAt", provenance.extractedAt().toString());
        return properties;
    }

    private <T> List<List<T>> pages(List<T> values) {
        List<T> immutable = List.copyOf(values);
        List<List<T>> result = new ArrayList<>();
        for (int start = 0; start < immutable.size(); start += writeBatchSize) {
            result.add(immutable.subList(
                    start,
                    Math.min(start + writeBatchSize, immutable.size())));
        }
        return List.copyOf(result);
    }

    private static List<UUID> requireIds(Collection<UUID> ids) {
        Objects.requireNonNull(ids, "ids");
        List<UUID> immutable = new ArrayList<>(ids);
        if (immutable.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("ids must not contain null values");
        }
        return List.copyOf(immutable);
    }

    private static String key(UUID batchId, UUID recordId) {
        return batchId + ":" + recordId;
    }

    private static UUID uuid(Map<String, Object> properties, String key) {
        return UUID.fromString(string(properties, key));
    }

    private static String string(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (value == null) {
            throw new Neo4jGraphProjectionException(
                    "Neo4j graph record is missing property " + key);
        }
        return value.toString();
    }

    private static Number number(Map<String, Object> properties, String key) {
        Object value = properties.get(key);
        if (!(value instanceof Number number)) {
            throw new Neo4jGraphProjectionException(
                    "Neo4j graph record has non-numeric property " + key);
        }
        return number;
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static void requirePageSize(int pageSize) {
        if (pageSize <= 0
                || pageSize
                        > AuthorizedGraphTraversalSource.MAXIMUM_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    "pageSize must be between 1 and "
                            + AuthorizedGraphTraversalSource.MAXIMUM_PAGE_SIZE);
        }
    }
}
