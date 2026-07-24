package com.orgmemory.graphrag.postgres;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.curation.EffectiveGraphCuration;
import com.orgmemory.graphrag.curation.GraphCurationRecord;
import com.orgmemory.graphrag.curation.GraphCurationStore;
import com.orgmemory.graphrag.curation.GraphIdentityRef;
import com.orgmemory.graphrag.export.GraphExportDocument;
import com.orgmemory.graphrag.export.GraphExportReader;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * PostgreSQL export adapter. Identity discovery and all contribution loading
 * are authorization-filtered before aggregation or formatting.
 */
public final class PostgresGraphExportReader implements GraphExportReader {

    private final NamedParameterJdbcTemplate jdbc;
    private final GraphStore graphs;
    private final ProjectionPublicationStore publications;
    private final GraphCurationStore curations;

    public PostgresGraphExportReader(
            NamedParameterJdbcTemplate jdbc,
            GraphStore graphs,
            ProjectionPublicationStore publications,
            GraphCurationStore curations) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.graphs = Objects.requireNonNull(graphs, "graphs");
        this.publications = Objects.requireNonNull(publications, "publications");
        this.curations = Objects.requireNonNull(curations, "curations");
    }

    @Override
    public GraphExportDocument read(
            AuthorizedEvidenceScope scope, ProjectionNamespace namespace) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(namespace, "namespace");
        if (!scope.organizationId().equals(namespace.organizationId())) {
            throw new IllegalArgumentException(
                    "authorization scope and export namespace must match");
        }
        if (scope.authorizedAssetIds().isEmpty()) {
            return new GraphExportDocument(List.of(), List.of());
        }
        ProjectionSnapshot snapshot = publications
                .current(namespace)
                .orElse(null);
        if (snapshot == null) {
            return new GraphExportDocument(List.of(), List.of());
        }
        IdentityCatalog catalog = visibleIdentities(scope, snapshot);
        List<EntityContribution> entityContributions =
                graphs.loadEntityContributions(scope, snapshot, catalog.entityIds());
        List<RelationContribution> relationContributions =
                graphs.loadRelationContributions(scope, snapshot, catalog.relationIds());
        EffectiveGraphCuration curation =
                EffectiveGraphCuration.from(
                        curations.active(scope, namespace, snapshot));
        return aggregate(entityContributions, relationContributions, curation);
    }

    private IdentityCatalog visibleIdentities(
            AuthorizedEvidenceScope scope,
            ProjectionSnapshot snapshot) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("batchId", snapshot.batchId())
                .addValue("organizationId", scope.organizationId())
                .addValue("authorizedAssetIds", scope.authorizedAssetIds());
        List<UUID> entityIds = jdbc.queryForList("""
                SELECT DISTINCT entity_id
                FROM projection_graph_entity_contributions
                WHERE batch_id = :batchId
                  AND organization_id = :organizationId
                  AND knowledge_asset_id IN (:authorizedAssetIds)
                ORDER BY entity_id
                """,
                parameters,
                UUID.class);
        List<UUID> relationIds = jdbc.queryForList("""
                SELECT DISTINCT relation_id
                FROM projection_graph_relation_contributions
                WHERE batch_id = :batchId
                  AND organization_id = :organizationId
                  AND knowledge_asset_id IN (:authorizedAssetIds)
                ORDER BY relation_id
                """,
                parameters,
                UUID.class);
        return new IdentityCatalog(entityIds, relationIds);
    }

    private static GraphExportDocument aggregate(
            List<EntityContribution> entityContributions,
            List<RelationContribution> relationContributions,
            EffectiveGraphCuration curation) {
        Map<UUID, EntityAccumulator> entities = new LinkedHashMap<>();
        for (EntityContribution contribution : entityContributions) {
            curation.effective(GraphIdentityRef.entity(contribution.entity().id()))
                    .ifPresent(identity -> entities
                            .computeIfAbsent(
                                    identity.id(), ignored -> new EntityAccumulator())
                            .add(
                                    contribution.entity().normalizedName(),
                                    contribution.type(),
                                    contribution.description(),
                                    contribution.provenance().evidence()));
        }
        for (GraphCurationRecord.CuratedEntity curated :
                curation.curatedEntities()) {
            curation.effective(curated.entity()).ifPresent(identity -> entities
                    .computeIfAbsent(identity.id(), ignored -> new EntityAccumulator())
                    .override(
                            curated.name(),
                            curated.type(),
                            curated.description(),
                            curated.governingEvidence()));
        }

        Map<UUID, RelationAccumulator> relations = new LinkedHashMap<>();
        for (RelationContribution contribution : relationContributions) {
            curation.effective(contribution.relation()).ifPresent(relation -> relations
                    .computeIfAbsent(
                            relation.id(),
                            ignored -> new RelationAccumulator(
                                    relation.sourceEntityId(),
                                    relation.targetEntityId()))
                    .add(
                            contribution.type(),
                            contribution.keywords(),
                            contribution.description(),
                            contribution.weight(),
                            contribution.provenance().evidence()));
        }
        for (GraphCurationRecord.CuratedRelation curated :
                curation.curatedRelations()) {
            var relationIdentity = curation.effective(curated.relation());
            var source = curation.effective(curated.sourceEntity());
            var target = curation.effective(curated.targetEntity());
            if (relationIdentity.isEmpty()
                    || source.isEmpty()
                    || target.isEmpty()
                    || source.orElseThrow().id().equals(target.orElseThrow().id())) {
                continue;
            }
            relations.computeIfAbsent(
                            relationIdentity.orElseThrow().id(),
                            ignored -> new RelationAccumulator(
                                    source.orElseThrow().id(),
                                    target.orElseThrow().id()))
                    .override(
                            curated.type(),
                            curated.keywords(),
                            curated.description(),
                            curated.weight(),
                            curated.governingEvidence());
        }

        List<GraphExportDocument.EntityRow> entityRows = entities.entrySet().stream()
                .map(entry -> entry.getValue().toRow(entry.getKey()))
                .sorted(Comparator.comparing(GraphExportDocument.EntityRow::id))
                .toList();
        Set<UUID> visibleEntityIds = entityRows.stream()
                .map(GraphExportDocument.EntityRow::id)
                .collect(Collectors.toUnmodifiableSet());
        List<GraphExportDocument.RelationRow> relationRows = relations.entrySet().stream()
                .filter(entry -> visibleEntityIds.contains(
                                entry.getValue().sourceEntityId)
                        && visibleEntityIds.contains(
                                entry.getValue().targetEntityId))
                .map(entry -> entry.getValue().toRow(entry.getKey()))
                .sorted(Comparator.comparing(GraphExportDocument.RelationRow::id))
                .toList();
        return new GraphExportDocument(entityRows, relationRows);
    }

    private record IdentityCatalog(
            List<UUID> entityIds, List<UUID> relationIds) {
    }

    private static final class EntityAccumulator {

        private final Set<String> names = new LinkedHashSet<>();
        private final Set<String> types = new LinkedHashSet<>();
        private final Set<String> descriptions = new LinkedHashSet<>();
        private final Set<EvidenceReference> evidence = new LinkedHashSet<>();
        private boolean curated;

        void add(
                String name,
                String type,
                String description,
                EvidenceReference reference) {
            names.add(name);
            types.add(type);
            descriptions.add(description);
            evidence.add(reference);
        }

        void override(
                String name,
                String type,
                String description,
                EvidenceReference reference) {
            if (!curated) {
                names.clear();
                types.clear();
                descriptions.clear();
                curated = true;
            }
            names.add(name);
            types.add(type);
            descriptions.add(description);
            evidence.add(reference);
        }

        GraphExportDocument.EntityRow toRow(UUID id) {
            return new GraphExportDocument.EntityRow(
                    id,
                    canonical(names),
                    canonical(types),
                    canonical(descriptions),
                    canonicalEvidence(evidence));
        }
    }

    private static final class RelationAccumulator {

        private final UUID sourceEntityId;
        private final UUID targetEntityId;
        private final Set<String> types = new LinkedHashSet<>();
        private final Set<String> keywords = new LinkedHashSet<>();
        private final Set<String> descriptions = new LinkedHashSet<>();
        private final Set<EvidenceReference> evidence = new LinkedHashSet<>();
        private double weight;
        private boolean curated;

        private RelationAccumulator(UUID sourceEntityId, UUID targetEntityId) {
            this.sourceEntityId = sourceEntityId;
            this.targetEntityId = targetEntityId;
        }

        void add(
                String type,
                List<String> addedKeywords,
                String description,
                double addedWeight,
                EvidenceReference reference) {
            types.add(type);
            keywords.addAll(addedKeywords);
            descriptions.add(description);
            weight += addedWeight;
            evidence.add(reference);
        }

        void override(
                String type,
                List<String> addedKeywords,
                String description,
                double addedWeight,
                EvidenceReference reference) {
            if (!curated) {
                types.clear();
                keywords.clear();
                descriptions.clear();
                weight = 0.0;
                curated = true;
            }
            add(type, addedKeywords, description, addedWeight, reference);
        }

        GraphExportDocument.RelationRow toRow(UUID id) {
            return new GraphExportDocument.RelationRow(
                    id,
                    sourceEntityId,
                    targetEntityId,
                    canonical(types),
                    keywords.stream().sorted().toList(),
                    canonical(descriptions),
                    weight,
                    canonicalEvidence(evidence));
        }
    }

    private static String canonical(Set<String> values) {
        return values.stream().sorted().collect(Collectors.joining("\n"));
    }

    private static List<EvidenceReference> canonicalEvidence(
            Set<EvidenceReference> evidence) {
        return evidence.stream()
                .sorted(Comparator.comparing(EvidenceReference::knowledgeAssetId)
                        .thenComparing(EvidenceReference::sourceRevisionId)
                        .thenComparing(
                                reference ->
                                        Objects.toString(reference.chunkId(), "")))
                .toList();
    }
}
