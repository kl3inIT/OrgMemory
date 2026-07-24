package com.orgmemory.graphrag.curation;

import com.orgmemory.graphrag.cache.CanonicalCacheKeyHasher;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical content identity for curation idempotency. */
public final class GraphCurationFingerprint {

    private GraphCurationFingerprint() {
    }

    public static String fingerprint(GraphCurationRecord record) {
        Objects.requireNonNull(record, "record");
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("organizationId", record.namespace().organizationId().toString());
        fields.put("workspace", record.namespace().workspace());
        fields.put("collection", record.namespace().collection());
        fields.put("actorUserId", record.provenance().actorUserId().toString());
        fields.put(
                "authorizationModelId",
                record.provenance().authorizationModelId());
        fields.put(
                "aclGeneration",
                Long.toString(record.provenance().aclGeneration()));
        fields.put("reason", record.provenance().reason());
        switch (record) {
            case GraphCurationRecord.CuratedEntity entity -> {
                fields.put("kind", "CURATED_ENTITY");
                fields.put("identity", entity.entity().id().toString());
                fields.put("name", entity.name());
                fields.put("type", entity.type());
                fields.put("description", entity.description());
                evidence(fields, entity.governingEvidence());
            }
            case GraphCurationRecord.CuratedRelation relation -> {
                fields.put("kind", "CURATED_RELATION");
                fields.put("identity", relation.relation().id().toString());
                fields.put(
                        "sourceEntity", relation.sourceEntity().id().toString());
                fields.put(
                        "targetEntity", relation.targetEntity().id().toString());
                fields.put("type", relation.type());
                fields.put("keywords", String.join("\u001f", relation.keywords()));
                fields.put("description", relation.description());
                fields.put("weight", Double.toHexString(relation.weight()));
                evidence(fields, relation.governingEvidence());
            }
            case GraphCurationRecord.IdentityAlias alias -> {
                fields.put("kind", "IDENTITY_ALIAS");
                fields.put("identityKind", alias.source().kind().name());
                fields.put("source", alias.source().id().toString());
                fields.put("target", alias.target().id().toString());
            }
            case GraphCurationRecord.IdentitySuppression suppression -> {
                fields.put("kind", "IDENTITY_SUPPRESSION");
                fields.put("identityKind", suppression.identity().kind().name());
                fields.put("identity", suppression.identity().id().toString());
            }
        }
        return CanonicalCacheKeyHasher.sha256(
                "orgmemory.graph-rag.curation.v1", fields);
    }

    private static void evidence(
            Map<String, String> fields,
            com.orgmemory.graphrag.model.EvidenceReference evidence) {
        fields.put(
                "governingKnowledgeAssetId",
                evidence.knowledgeAssetId().toString());
        fields.put(
                "governingSourceRevisionId",
                evidence.sourceRevisionId().toString());
        fields.put(
                "governingChunkId", Objects.toString(evidence.chunkId(), ""));
        fields.put(
                "governingAclSnapshotId", evidence.aclSnapshotId().toString());
        fields.put(
                "governingAclGeneration",
                Long.toString(evidence.aclGeneration()));
    }
}
