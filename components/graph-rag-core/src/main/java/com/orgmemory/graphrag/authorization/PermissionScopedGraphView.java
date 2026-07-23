package com.orgmemory.graphrag.authorization;

import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.summarization.ScopedDescriptionSet;
import java.util.List;
import java.util.Objects;

public record PermissionScopedGraphView(
        String authorizationFingerprint,
        String projectionFingerprint,
        List<EntityView> entities,
        List<RelationView> relations) {

    public PermissionScopedGraphView {
        authorizationFingerprint =
                requireText(authorizationFingerprint, "authorizationFingerprint");
        projectionFingerprint =
                requireText(projectionFingerprint, "projectionFingerprint");
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        relations = List.copyOf(Objects.requireNonNull(relations, "relations"));
    }

    public record EntityView(
            CanonicalEntity entity,
            List<String> types,
            List<String> descriptions,
            List<EvidenceReference> evidence,
            double confidence,
            String authorizationFingerprint,
            String projectionFingerprint) {

        public EntityView {
            Objects.requireNonNull(entity, "entity");
            types = List.copyOf(Objects.requireNonNull(types, "types"));
            descriptions = List.copyOf(Objects.requireNonNull(descriptions, "descriptions"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            if (types.isEmpty() || descriptions.isEmpty() || evidence.isEmpty()) {
                throw new IllegalArgumentException(
                        "scoped entity views require types, descriptions and evidence");
            }
            requireConfidence(confidence);
            authorizationFingerprint =
                    requireText(authorizationFingerprint, "authorizationFingerprint");
            projectionFingerprint =
                    requireText(projectionFingerprint, "projectionFingerprint");
        }

        public ScopedDescriptionSet summaryInput() {
            return new ScopedDescriptionSet(
                    entity.id(),
                    "Entity",
                    entity.normalizedName(),
                    descriptions,
                    authorizationFingerprint,
                    projectionFingerprint);
        }
    }

    public record RelationView(
            CanonicalRelation relation,
            List<String> types,
            List<String> keywords,
            List<String> descriptions,
            List<EvidenceReference> evidence,
            double weight,
            double confidence,
            String authorizationFingerprint,
            String projectionFingerprint) {

        public RelationView {
            Objects.requireNonNull(relation, "relation");
            types = List.copyOf(Objects.requireNonNull(types, "types"));
            keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
            descriptions = List.copyOf(Objects.requireNonNull(descriptions, "descriptions"));
            evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
            if (types.isEmpty() || descriptions.isEmpty() || evidence.isEmpty()) {
                throw new IllegalArgumentException(
                        "scoped relation views require types, descriptions and evidence");
            }
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException("weight must be finite and positive");
            }
            requireConfidence(confidence);
            authorizationFingerprint =
                    requireText(authorizationFingerprint, "authorizationFingerprint");
            projectionFingerprint =
                    requireText(projectionFingerprint, "projectionFingerprint");
        }

        public ScopedDescriptionSet summaryInput() {
            return new ScopedDescriptionSet(
                    relation.id(),
                    "Relation",
                    relation.sourceEntityId() + " -> " + relation.targetEntityId(),
                    descriptions,
                    authorizationFingerprint,
                    projectionFingerprint);
        }
    }

    private static void requireConfidence(double confidence) {
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0 and 1");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
