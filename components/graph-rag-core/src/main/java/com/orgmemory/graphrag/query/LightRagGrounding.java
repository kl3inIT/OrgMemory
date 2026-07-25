package com.orgmemory.graphrag.query;

import static com.orgmemory.graphrag.validation.TextValidation.requireText;

import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutral, token-bounded context selected by the LightRAG runtime.
 *
 * <p>Selections preserve contribution-level provenance so an application shell
 * can close and recheck the complete evidence set before model delivery.
 */
public record LightRagGrounding(
        List<SelectedEntity> entities,
        List<SelectedRelation> relations,
        List<SelectedChunk> chunks,
        List<ScopeSnapshot> scopes,
        ContextTokenUsage tokenUsage,
        int chunkTokens) {

    public LightRagGrounding {
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
        chunks = List.copyOf(Objects.requireNonNull(chunks, "chunks"));
        scopes = List.copyOf(Objects.requireNonNull(scopes, "scopes"));
        Objects.requireNonNull(tokenUsage, "tokenUsage");
        if (chunkTokens < 0) {
            throw new IllegalArgumentException("chunkTokens must be non-negative");
        }
    }

    public boolean empty() {
        return entities.isEmpty() && relations.isEmpty() && chunks.isEmpty();
    }

    public List<GroundingEvidence> evidenceClosure() {
        LinkedHashMap<UUID, GroundingEvidence> closure = new LinkedHashMap<>();
        entities.forEach(entity -> entity.contributions().forEach(contribution ->
                addEvidence(
                        closure,
                        contribution.evidence(),
                        contribution.projectionGeneration())));
        relations.forEach(relation -> relation.contributions().forEach(contribution ->
                addEvidence(
                        closure,
                        contribution.evidence(),
                        contribution.projectionGeneration())));
        chunks.forEach(chunk -> addEvidence(
                closure,
                chunk.evidence(),
                chunk.projectionGeneration()));
        return List.copyOf(closure.values());
    }

    private static void addEvidence(
            Map<UUID, GroundingEvidence> closure,
            EvidenceReference evidence,
            long projectionGeneration) {
        if (evidence.chunkId() == null) {
            throw new IllegalStateException(
                    "grounding contributions require chunk-level evidence");
        }
        GroundingEvidence candidate =
                new GroundingEvidence(evidence, projectionGeneration);
        GroundingEvidence existing = closure.putIfAbsent(
                evidence.chunkId(),
                candidate);
        if (existing != null && !existing.equals(candidate)) {
            throw new IllegalStateException(
                    "one grounding chunk resolved to conflicting evidence");
        }
    }

    public record ScopeSnapshot(
            ProjectionNamespace namespace,
            UUID batchId,
            long projectionGeneration,
            String manifestHash,
            String authorizationFingerprint) {

        public ScopeSnapshot {
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(batchId, "batchId");
            if (projectionGeneration <= 0) {
                throw new IllegalArgumentException(
                        "projectionGeneration must be positive");
            }
            manifestHash = requireText(manifestHash, "manifestHash");
            authorizationFingerprint =
                    requireText(authorizationFingerprint, "authorizationFingerprint");
        }
    }

    public record GroundingEvidence(
            EvidenceReference evidence,
            long projectionGeneration) {

        public GroundingEvidence {
            Objects.requireNonNull(evidence, "evidence");
            if (evidence.chunkId() == null) {
                throw new IllegalArgumentException(
                        "grounding evidence must identify a chunk");
            }
            if (projectionGeneration <= 0) {
                throw new IllegalArgumentException(
                        "projectionGeneration must be positive");
            }
        }
    }

    public record SelectedEntity(
            UUID id,
            String name,
            List<EntityContribution> contributions,
            double relevanceScore,
            int order) {

        public SelectedEntity {
            Objects.requireNonNull(id, "id");
            name = requireText(name, "name");
            contributions =
                    List.copyOf(Objects.requireNonNull(contributions, "contributions"));
            if (contributions.isEmpty()) {
                throw new IllegalArgumentException(
                        "selected entities require contributions");
            }
            requireScore(relevanceScore);
            requireOrder(order);
        }
    }

    public record EntityContribution(
            String type,
            String description,
            EvidenceReference evidence,
            long projectionGeneration,
            double confidence) {

        public EntityContribution {
            type = requireText(type, "type");
            description = requireText(description, "description");
            Objects.requireNonNull(evidence, "evidence");
            requireGeneration(projectionGeneration);
            requireConfidence(confidence);
        }
    }

    public record SelectedRelation(
            UUID id,
            UUID sourceEntityId,
            UUID targetEntityId,
            String sourceEntityName,
            String targetEntityName,
            List<RelationContribution> contributions,
            double relevanceScore,
            int order) {

        public SelectedRelation {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sourceEntityId, "sourceEntityId");
            Objects.requireNonNull(targetEntityId, "targetEntityId");
            sourceEntityName = requireText(sourceEntityName, "sourceEntityName");
            targetEntityName = requireText(targetEntityName, "targetEntityName");
            contributions =
                    List.copyOf(Objects.requireNonNull(contributions, "contributions"));
            if (contributions.isEmpty()) {
                throw new IllegalArgumentException(
                        "selected relations require contributions");
            }
            requireScore(relevanceScore);
            requireOrder(order);
        }
    }

    public record RelationContribution(
            String type,
            List<String> keywords,
            String description,
            double weight,
            EvidenceReference evidence,
            long projectionGeneration,
            double confidence) {

        public RelationContribution {
            type = requireText(type, "type");
            keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
            if (keywords.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException(
                        "keywords must not contain blank values");
            }
            description = requireText(description, "description");
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException(
                        "weight must be finite and positive");
            }
            Objects.requireNonNull(evidence, "evidence");
            requireGeneration(projectionGeneration);
            requireConfidence(confidence);
        }
    }

    public record SelectedChunk(
            UUID id,
            EvidenceReference evidence,
            long projectionGeneration,
            String content,
            Map<String, String> metadata,
            LightRagQueryResult.Origin origin,
            int frequency,
            int order,
            double retrievalScore,
            Double rerankScore) {

        public SelectedChunk {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(evidence, "evidence");
            if (!id.equals(evidence.chunkId())) {
                throw new IllegalArgumentException(
                        "selected chunk identity must match its evidence");
            }
            requireGeneration(projectionGeneration);
            content = Objects.requireNonNull(content, "content");
            metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
            Objects.requireNonNull(origin, "origin");
            if (frequency <= 0) {
                throw new IllegalArgumentException(
                        "frequency must be positive");
            }
            requireOrder(order);
            requireScore(retrievalScore);
            if (rerankScore != null) {
                requireScore(rerankScore);
            }
        }

        public double effectiveScore() {
            return rerankScore == null ? retrievalScore : rerankScore;
        }
    }

    static List<EntityContribution> entityContributions(
            com.orgmemory.graphrag.authorization.PermissionScopedGraphView.EntityView view) {
        List<EntityContribution> values = new ArrayList<>(view.contributions().size());
        view.contributions().forEach(contribution -> values.add(new EntityContribution(
                contribution.type(),
                contribution.description(),
                contribution.evidence(),
                contribution.projectionGeneration(),
                contribution.confidence())));
        return List.copyOf(values);
    }

    static List<RelationContribution> relationContributions(
            com.orgmemory.graphrag.authorization.PermissionScopedGraphView.RelationView view) {
        List<RelationContribution> values =
                new ArrayList<>(view.contributions().size());
        view.contributions().forEach(contribution -> values.add(new RelationContribution(
                contribution.type(),
                contribution.keywords(),
                contribution.description(),
                contribution.weight(),
                contribution.evidence(),
                contribution.projectionGeneration(),
                contribution.confidence())));
        return List.copyOf(values);
    }

    private static void requireGeneration(long value) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "projectionGeneration must be positive");
        }
    }

    private static void requireOrder(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("order must be positive");
        }
    }

    private static void requireScore(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("score must be finite");
        }
    }

    private static void requireConfidence(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be between 0 and 1");
        }
    }
}
