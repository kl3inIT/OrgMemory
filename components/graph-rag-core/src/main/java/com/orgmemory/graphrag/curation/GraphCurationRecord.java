package com.orgmemory.graphrag.curation;

import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Append-only graph management records. Storage adapters must return only
 * records whose governing evidence and namespace are visible to the supplied
 * authorization scope.
 */
public sealed interface GraphCurationRecord {

    UUID id();

    ProjectionNamespace namespace();

    CurationProvenance provenance();

    record CuratedEntity(
            UUID id,
            ProjectionNamespace namespace,
            GraphIdentityRef entity,
            String name,
            String type,
            String description,
            EvidenceReference governingEvidence,
            CurationProvenance provenance)
            implements GraphCurationRecord {

        public CuratedEntity {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(namespace, "namespace");
            requireKind(entity, GraphIdentityKind.ENTITY, "entity");
            name = requireText(name, "name");
            type = requireText(type, "type");
            description = requireText(description, "description");
            requireMatchingOrganization(namespace, governingEvidence);
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    record CuratedRelation(
            UUID id,
            ProjectionNamespace namespace,
            GraphIdentityRef relation,
            GraphIdentityRef sourceEntity,
            GraphIdentityRef targetEntity,
            String type,
            List<String> keywords,
            String description,
            double weight,
            EvidenceReference governingEvidence,
            CurationProvenance provenance)
            implements GraphCurationRecord {

        public CuratedRelation {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(namespace, "namespace");
            requireKind(relation, GraphIdentityKind.RELATION, "relation");
            requireKind(sourceEntity, GraphIdentityKind.ENTITY, "sourceEntity");
            requireKind(targetEntity, GraphIdentityKind.ENTITY, "targetEntity");
            if (sourceEntity.equals(targetEntity)) {
                throw new IllegalArgumentException(
                        "a curated relation must connect different entities");
            }
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
            requireMatchingOrganization(namespace, governingEvidence);
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    record IdentityAlias(
            UUID id,
            ProjectionNamespace namespace,
            GraphIdentityRef source,
            GraphIdentityRef target,
            CurationProvenance provenance)
            implements GraphCurationRecord {

        public IdentityAlias {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(target, "target");
            Objects.requireNonNull(provenance, "provenance");
            if (source.kind() != target.kind()) {
                throw new IllegalArgumentException(
                        "an identity alias cannot cross identity kinds");
            }
            if (source.equals(target)) {
                throw new IllegalArgumentException(
                        "an identity alias must change the identity");
            }
        }
    }

    record IdentitySuppression(
            UUID id,
            ProjectionNamespace namespace,
            GraphIdentityRef identity,
            CurationProvenance provenance)
            implements GraphCurationRecord {

        public IdentitySuppression {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(namespace, "namespace");
            Objects.requireNonNull(identity, "identity");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    private static void requireKind(
            GraphIdentityRef value, GraphIdentityKind expected, String field) {
        Objects.requireNonNull(value, field);
        if (value.kind() != expected) {
            throw new IllegalArgumentException(field + " must be " + expected);
        }
    }

    private static void requireMatchingOrganization(
            ProjectionNamespace namespace, EvidenceReference evidence) {
        Objects.requireNonNull(evidence, "governingEvidence");
        if (!namespace.organizationId().equals(evidence.organizationId())) {
            throw new IllegalArgumentException(
                    "governing evidence must belong to the curation organization");
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
