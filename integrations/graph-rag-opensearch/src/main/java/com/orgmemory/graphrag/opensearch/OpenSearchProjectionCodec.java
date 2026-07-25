package com.orgmemory.graphrag.opensearch;

import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.LexicalIndex;
import com.orgmemory.graphrag.storage.ProcessingStatusIndex;
import com.orgmemory.graphrag.storage.ProjectionBatch;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import com.orgmemory.graphrag.storage.VectorIndex;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class OpenSearchProjectionCodec {

    static final String ORGANIZATION_ID = "organization_id";
    static final String WORKSPACE = "workspace";
    static final String COLLECTION = "collection_name";
    static final String BATCH_ID = "batch_id";
    static final String GENERATION = "generation";
    static final String RECORD_ID = "record_id";
    static final String ASSET_ID = "knowledge_asset_id";
    static final String REVISION_ID = "source_revision_id";
    static final String CHUNK_ID = "chunk_id";
    static final String ACL_SNAPSHOT_ID = "acl_snapshot_id";
    static final String ACL_GENERATION = "acl_generation";

    private OpenSearchProjectionCodec() {
    }

    static Map<String, Object> namespace(ProjectionNamespace namespace) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(ORGANIZATION_ID, namespace.organizationId().toString());
        document.put(WORKSPACE, namespace.workspace());
        document.put(COLLECTION, namespace.collection());
        return document;
    }

    static Map<String, Object> staged(
            ProjectionBatch batch,
            String recordId,
            EvidenceReference evidence) {
        Map<String, Object> document = namespace(batch.namespace());
        document.put(BATCH_ID, batch.id().toString());
        document.put(GENERATION, batch.generation());
        document.put(RECORD_ID, recordId);
        evidence(document, evidence);
        return document;
    }

    static Map<String, Object> content(
            ProjectionBatch batch,
            ContentStore.ContentRecord record) {
        Map<String, Object> document = staged(batch, record.id(), record.evidence());
        document.put("content_kind", record.kind().name());
        document.put("content", record.content());
        document.put("token_count", record.tokenCount());
        document.put("metadata", record.metadata());
        return document;
    }

    static ContentStore.ContentRecord content(Map<String, Object> document) {
        return new ContentStore.ContentRecord(
                string(document, RECORD_ID),
                evidence(document),
                ContentStore.ContentKind.valueOf(string(document, "content_kind")),
                string(document, "content"),
                integer(document, "token_count"),
                stringMap(document.get("metadata")));
    }

    static Map<String, Object> lexical(
            ProjectionBatch batch,
            LexicalIndex.LexicalDocument record) {
        Map<String, Object> document = staged(batch, record.id(), record.evidence());
        document.put("content", record.content());
        document.put("fields", record.fields());
        document.put(
                "search_fields",
                record.fields().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> Map.of(
                                "name", entry.getKey(),
                                "value", entry.getValue()))
                        .toList());
        document.put(
                "search_text",
                record.content() + '\n' + String.join("\n", record.fields().values()));
        return document;
    }

    static LexicalIndex.LexicalDocument lexical(Map<String, Object> document) {
        return new LexicalIndex.LexicalDocument(
                string(document, RECORD_ID),
                evidence(document),
                string(document, "content"),
                stringMap(document.get("fields")));
    }

    static Map<String, Object> vector(
            ProjectionBatch batch,
            VectorIndex.VectorRecord record) {
        Map<String, Object> document = staged(batch, record.id(), record.evidence());
        document.put("subject_id", record.subjectId());
        document.put("vector_kind", record.kind().name());
        document.put("embedding_profile_id", record.embeddingProfileId().toString());
        document.put("model", record.model());
        document.put("dimensions", record.vector().dimensions());
        document.put("vector", floats(record.vector()));
        document.put("metadata", record.metadata());
        return document;
    }

    static VectorIndex.VectorRecord vector(Map<String, Object> document) {
        return new VectorIndex.VectorRecord(
                string(document, RECORD_ID),
                string(document, "subject_id"),
                evidence(document),
                VectorIndex.VectorKind.valueOf(string(document, "vector_kind")),
                uuid(document, "embedding_profile_id"),
                string(document, "model"),
                vector(document.get("vector")),
                stringMap(document.get("metadata")));
    }

    static Map<String, Object> entity(
            ProjectionBatch batch,
            EntityContribution contribution) {
        Map<String, Object> document =
                staged(batch, contribution.id().toString(), contribution.provenance().evidence());
        document.put("document_kind", "ENTITY");
        document.put("entity_id", contribution.entity().id().toString());
        document.put("normalized_name", contribution.entity().normalizedName());
        document.put("contribution_type", contribution.type());
        document.put("description", contribution.description());
        provenance(document, contribution.provenance());
        return document;
    }

    static EntityContribution entity(Map<String, Object> document) {
        return new EntityContribution(
                uuid(document, RECORD_ID),
                new CanonicalEntity(
                        uuid(document, "entity_id"),
                        string(document, "normalized_name")),
                string(document, "contribution_type"),
                string(document, "description"),
                provenance(document));
    }

    static Map<String, Object> relation(
            ProjectionBatch batch,
            RelationContribution contribution) {
        Map<String, Object> document =
                staged(batch, contribution.id().toString(), contribution.provenance().evidence());
        document.put("document_kind", "RELATION");
        document.put("relation_id", contribution.relation().id().toString());
        document.put(
                "source_entity_id",
                contribution.relation().sourceEntityId().toString());
        document.put(
                "target_entity_id",
                contribution.relation().targetEntityId().toString());
        document.put("orientation", contribution.relation().orientation().name());
        document.put("contribution_type", contribution.type());
        document.put("keywords", contribution.keywords());
        document.put("description", contribution.description());
        document.put("weight", contribution.weight());
        provenance(document, contribution.provenance());
        return document;
    }

    static RelationContribution relation(Map<String, Object> document) {
        return new RelationContribution(
                uuid(document, RECORD_ID),
                new CanonicalRelation(
                        uuid(document, "relation_id"),
                        uuid(document, "source_entity_id"),
                        uuid(document, "target_entity_id"),
                        RelationOrientation.valueOf(string(document, "orientation"))),
                string(document, "contribution_type"),
                stringList(document.get("keywords")),
                string(document, "description"),
                decimal(document, "weight"),
                provenance(document));
    }

    static Map<String, Object> status(ProcessingStatusIndex.StatusRecord record) {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put(ORGANIZATION_ID, record.organizationId().toString());
        document.put(REVISION_ID, record.sourceRevisionId().toString());
        document.put("worker_job_id", record.workerJobId().toString());
        document.put("status", record.state().name());
        document.put("content_sha256", record.contentSha256());
        document.put("error_code", record.errorCode());
        document.put("observed_at", record.observedAt().toString());
        document.put("metadata", record.metadata());
        return document;
    }

    static ProcessingStatusIndex.StatusRecord status(Map<String, Object> document) {
        return new ProcessingStatusIndex.StatusRecord(
                uuid(document, ORGANIZATION_ID),
                uuid(document, REVISION_ID),
                uuid(document, "worker_job_id"),
                ProcessingStatusIndex.State.valueOf(string(document, "status")),
                string(document, "content_sha256"),
                nullableString(document.get("error_code")),
                Instant.parse(string(document, "observed_at")),
                stringMap(document.get("metadata")));
    }

    static Map<String, Object> batch(ProjectionBatch batch, String status) {
        Map<String, Object> document = namespace(batch.namespace());
        document.put("document_kind", "BATCH");
        document.put(BATCH_ID, batch.id().toString());
        document.put("expected_previous_generation", batch.expectedPreviousGeneration());
        document.put(GENERATION, batch.generation());
        document.put("idempotency_key", batch.idempotencyKey());
        document.put("manifest_fingerprint", batch.manifestFingerprint());
        document.put("required_projections", kinds(batch.requiredProjections()));
        document.put("status", status);
        document.put("created_at", batch.createdAt().toString());
        return document;
    }

    static ProjectionBatch batch(Map<String, Object> document) {
        return new ProjectionBatch(
                uuid(document, BATCH_ID),
                namespace(document),
                number(document, "expected_previous_generation"),
                number(document, GENERATION),
                string(document, "idempotency_key"),
                string(document, "manifest_fingerprint"),
                kinds(document.get("required_projections")),
                Instant.parse(string(document, "created_at")));
    }

    static Map<String, Object> publication(
            ProjectionBatch batch,
            Instant publishedAt) {
        Map<String, Object> document = batch(batch, "PUBLISHED");
        document.put("document_kind", "PUBLICATION");
        document.put("published_at", publishedAt.toString());
        return document;
    }

    static ProjectionSnapshot snapshot(Map<String, Object> document) {
        return new ProjectionSnapshot(
                uuid(document, BATCH_ID),
                namespace(document),
                number(document, GENERATION),
                string(document, "manifest_fingerprint"),
                kinds(document.get("required_projections")),
                Instant.parse(string(document, "published_at")));
    }

    static ProjectionNamespace namespace(Map<String, Object> document) {
        return new ProjectionNamespace(
                uuid(document, ORGANIZATION_ID),
                string(document, WORKSPACE),
                string(document, COLLECTION));
    }

    private static void evidence(
            Map<String, Object> document,
            EvidenceReference evidence) {
        document.put(ORGANIZATION_ID, evidence.organizationId().toString());
        document.put(ASSET_ID, evidence.knowledgeAssetId().toString());
        document.put(REVISION_ID, evidence.sourceRevisionId().toString());
        document.put(CHUNK_ID, evidence.chunkId() == null ? null : evidence.chunkId().toString());
        document.put(ACL_SNAPSHOT_ID, evidence.aclSnapshotId().toString());
        document.put(ACL_GENERATION, evidence.aclGeneration());
    }

    private static EvidenceReference evidence(Map<String, Object> document) {
        return new EvidenceReference(
                uuid(document, ORGANIZATION_ID),
                uuid(document, ASSET_ID),
                uuid(document, REVISION_ID),
                nullableUuid(document.get(CHUNK_ID)),
                uuid(document, ACL_SNAPSHOT_ID),
                number(document, ACL_GENERATION));
    }

    private static void provenance(
            Map<String, Object> document,
            EvidenceProvenance provenance) {
        document.put("projection_generation", provenance.projectionGeneration());
        document.put("extractor_provider", provenance.extractorProvider());
        document.put("extractor_model", provenance.extractorModel());
        document.put("prompt_version", provenance.promptVersion());
        document.put(
                "extraction_profile_fingerprint",
                provenance.extractionProfileFingerprint());
        document.put("confidence", provenance.confidence());
        document.put("extracted_at", provenance.extractedAt().toString());
    }

    private static EvidenceProvenance provenance(Map<String, Object> document) {
        return new EvidenceProvenance(
                evidence(document),
                number(document, "projection_generation"),
                string(document, "extractor_provider"),
                string(document, "extractor_model"),
                string(document, "prompt_version"),
                string(document, "extraction_profile_fingerprint"),
                decimal(document, "confidence"),
                Instant.parse(string(document, "extracted_at")));
    }

    private static List<Float> floats(FloatVector vector) {
        List<Float> values = new ArrayList<>(vector.dimensions());
        for (float value : vector.copyValues()) {
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static FloatVector vector(Object value) {
        if (!(value instanceof Collection<?> values)) {
            throw new IllegalArgumentException("vector is not an array");
        }
        float[] vector = new float[values.size()];
        int index = 0;
        for (Object item : values) {
            if (!(item instanceof Number number)) {
                throw new IllegalArgumentException("vector contains a non-number");
            }
            vector[index++] = number.floatValue();
        }
        return new FloatVector(vector);
    }

    private static Set<String> kinds(Set<ProjectionKind> kinds) {
        return kinds.stream().map(Enum::name).collect(Collectors.toUnmodifiableSet());
    }

    private static Set<ProjectionKind> kinds(Object value) {
        EnumSet<ProjectionKind> kinds = EnumSet.noneOf(ProjectionKind.class);
        for (String item : stringList(value)) {
            kinds.add(ProjectionKind.valueOf(item));
        }
        return Set.copyOf(kinds);
    }

    private static String string(Map<String, Object> document, String field) {
        Object value = document.get(field);
        if (value == null) {
            throw new IllegalArgumentException("missing field " + field);
        }
        return value.toString();
    }

    private static String nullableString(Object value) {
        return value == null ? null : value.toString();
    }

    private static UUID uuid(Map<String, Object> document, String field) {
        return UUID.fromString(string(document, field));
    }

    private static UUID nullableUuid(Object value) {
        return value == null ? null : UUID.fromString(value.toString());
    }

    private static long number(Map<String, Object> document, String field) {
        Object value = document.get(field);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("field " + field + " is not numeric");
        }
        return number.longValue();
    }

    private static int integer(Map<String, Object> document, String field) {
        return Math.toIntExact(number(document, field));
    }

    private static double decimal(Map<String, Object> document, String field) {
        Object value = document.get(field);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("field " + field + " is not numeric");
        }
        return number.doubleValue();
    }

    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(key.toString(), item.toString()));
        return Map.copyOf(result);
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream().map(Object::toString).toList();
    }
}
