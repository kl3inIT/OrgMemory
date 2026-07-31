package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.shared.error.BusinessValidationException;
import com.orgmemory.graphrag.curation.GraphIdentityKind;
import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.List;
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
            requireValue(entityId, "entityId");
            requireText(name, "name");
            requireText(type, "type");
            requireText(description, "description");
            requireValue(governingEvidence, "governingEvidence");
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
            requireValue(relationId, "relationId");
            requireValue(sourceEntityId, "sourceEntityId");
            requireValue(targetEntityId, "targetEntityId");
            requireText(type, "type");
            keywords = List.copyOf(requireValue(keywords, "keywords"));
            requireText(description, "description");
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw invalid(
                        "weight must be finite and positive");
            }
            requireValue(governingEvidence, "governingEvidence");
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
            requireValue(kind, "kind");
            requireValue(sourceIdentityId, "sourceIdentityId");
            requireValue(targetIdentityId, "targetIdentityId");
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
            requireValue(kind, "kind");
            requireValue(identityId, "identityId");
        }
    }

    private static void requireCommon(
            UUID knowledgeSpaceId,
            String idempotencyKey,
            String reason,
            long authorizationGeneration) {
        requireValue(knowledgeSpaceId, "knowledgeSpaceId");
        requireText(idempotencyKey, "idempotencyKey");
        requireText(reason, "reason");
        if (authorizationGeneration < 0) {
            throw invalid(
                    "authorizationGeneration must be non-negative");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) {
            throw invalid(field + " must not be blank");
        }
        return normalized;
    }

    private static <T> T requireValue(T value, String field) {
        if (value == null) {
            throw invalid(field + " is required");
        }
        return value;
    }

    private static BusinessValidationException invalid(String message) {
        return new BusinessValidationException(
                "knowledge-graph.curation-invalid",
                message);
    }
}
