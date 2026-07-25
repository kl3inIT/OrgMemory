package com.orgmemory.core.knowledge;

import com.orgmemory.graphrag.curation.GraphIdentityKind;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public sealed interface KnowledgeGraphCurationCommand {

    UUID knowledgeSpaceId();

    String idempotencyKey();

    String reason();

    long authorizationGeneration();

    record CurateEntity(
            UUID knowledgeSpaceId,
            String idempotencyKey,
            String reason,
            long authorizationGeneration,
            UUID entityId,
            String name,
            String type,
            String description,
            EvidenceReference governingEvidence)
            implements KnowledgeGraphCurationCommand {

        public CurateEntity {
            requireCommon(
                    knowledgeSpaceId,
                    idempotencyKey,
                    reason,
                    authorizationGeneration);
            Objects.requireNonNull(entityId, "entityId");
            requireText(name, "name");
            requireText(type, "type");
            requireText(description, "description");
            Objects.requireNonNull(governingEvidence, "governingEvidence");
        }
    }

    record CurateRelation(
            UUID knowledgeSpaceId,
            String idempotencyKey,
            String reason,
            long authorizationGeneration,
            UUID relationId,
            UUID sourceEntityId,
            UUID targetEntityId,
            String type,
            List<String> keywords,
            String description,
            double weight,
            EvidenceReference governingEvidence)
            implements KnowledgeGraphCurationCommand {

        public CurateRelation {
            requireCommon(
                    knowledgeSpaceId,
                    idempotencyKey,
                    reason,
                    authorizationGeneration);
            Objects.requireNonNull(relationId, "relationId");
            Objects.requireNonNull(sourceEntityId, "sourceEntityId");
            Objects.requireNonNull(targetEntityId, "targetEntityId");
            requireText(type, "type");
            keywords = List.copyOf(Objects.requireNonNull(keywords, "keywords"));
            requireText(description, "description");
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException(
                        "weight must be finite and positive");
            }
            Objects.requireNonNull(governingEvidence, "governingEvidence");
        }
    }

    record AliasIdentity(
            UUID knowledgeSpaceId,
            String idempotencyKey,
            String reason,
            long authorizationGeneration,
            GraphIdentityKind kind,
            UUID sourceIdentityId,
            UUID targetIdentityId)
            implements KnowledgeGraphCurationCommand {

        public AliasIdentity {
            requireCommon(
                    knowledgeSpaceId,
                    idempotencyKey,
                    reason,
                    authorizationGeneration);
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(sourceIdentityId, "sourceIdentityId");
            Objects.requireNonNull(targetIdentityId, "targetIdentityId");
        }
    }

    record SuppressIdentity(
            UUID knowledgeSpaceId,
            String idempotencyKey,
            String reason,
            long authorizationGeneration,
            GraphIdentityKind kind,
            UUID identityId)
            implements KnowledgeGraphCurationCommand {

        public SuppressIdentity {
            requireCommon(
                    knowledgeSpaceId,
                    idempotencyKey,
                    reason,
                    authorizationGeneration);
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(identityId, "identityId");
        }
    }

    private static void requireCommon(
            UUID knowledgeSpaceId,
            String idempotencyKey,
            String reason,
            long authorizationGeneration) {
        Objects.requireNonNull(knowledgeSpaceId, "knowledgeSpaceId");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(reason, "reason");
        if (authorizationGeneration < 0) {
            throw new IllegalArgumentException(
                    "authorizationGeneration must be non-negative");
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
