package com.orgmemory.graphrag.opensearch;

import java.util.List;
import java.util.function.Consumer;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;

final class OpenSearchSchemas {

    private static final List<String> STAGED_KEYWORDS = List.of(
            OpenSearchProjectionCodec.ORGANIZATION_ID,
            OpenSearchProjectionCodec.WORKSPACE,
            OpenSearchProjectionCodec.COLLECTION,
            OpenSearchProjectionCodec.BATCH_ID,
            OpenSearchProjectionCodec.RECORD_ID,
            OpenSearchProjectionCodec.ASSET_ID,
            OpenSearchProjectionCodec.REVISION_ID,
            OpenSearchProjectionCodec.CHUNK_ID,
            OpenSearchProjectionCodec.ACL_SNAPSHOT_ID);

    private OpenSearchSchemas() {
    }

    static Consumer<CreateIndexRequest.Builder> control() {
        return request -> request.mappings(mapping -> {
            for (String field : List.of(
                    "document_kind",
                    OpenSearchProjectionCodec.ORGANIZATION_ID,
                    OpenSearchProjectionCodec.WORKSPACE,
                    OpenSearchProjectionCodec.COLLECTION,
                    OpenSearchProjectionCodec.BATCH_ID,
                    "idempotency_key",
                    "manifest_fingerprint",
                    "status",
                    "projection_kind")) {
                mapping.properties(field, property -> property.keyword(keyword -> keyword));
            }
            mapping.properties(
                    OpenSearchProjectionCodec.GENERATION,
                    property -> property.long_(number -> number));
            mapping.properties(
                    "expected_previous_generation",
                    property -> property.long_(number -> number));
            for (String field : List.of(
                    "created_at",
                    "prepared_at",
                    "published_at",
                    "aborted_at")) {
                mapping.properties(field, property -> property.date(date -> date));
            }
            return mapping;
        });
    }

    static Consumer<CreateIndexRequest.Builder> content() {
        return request -> request.mappings(mapping -> {
            staged(mapping);
            mapping.properties("content_kind", property -> property.keyword(keyword -> keyword));
            mapping.properties("content", property -> property.text(text -> text));
            mapping.properties("token_count", property -> property.integer(number -> number));
            disabledObject(mapping, "metadata");
            return mapping;
        });
    }

    static Consumer<CreateIndexRequest.Builder> lexical() {
        return request -> request.mappings(mapping -> {
            staged(mapping);
            mapping.properties("content", property -> property.text(text -> text));
            mapping.properties("search_text", property -> property.text(text -> text));
            mapping.properties(
                    "fields",
                    property -> property.object(object -> object.enabled(false)));
            mapping.properties(
                    "search_fields",
                    property -> property.nested(nested -> nested
                            .properties(
                                    "name",
                                    field -> field.keyword(keyword -> keyword))
                            .properties(
                                    "value",
                                    field -> field.text(text -> text))));
            return mapping;
        });
    }

    static Consumer<CreateIndexRequest.Builder> graphEntities() {
        return request -> request.mappings(mapping -> {
            staged(mapping);
            for (String field : List.of(
                    "document_kind",
                    "entity_id",
                    "contribution_type",
                    "extractor_provider",
                    "extractor_model",
                    "prompt_version",
                    "extraction_profile_fingerprint")) {
                mapping.properties(field, property -> property.keyword(keyword -> keyword));
            }
            mapping.properties("normalized_name", property -> property.text(text -> text));
            mapping.properties("description", property -> property.text(text -> text));
            mapping.properties("projection_generation", property -> property.long_(number -> number));
            mapping.properties("confidence", property -> property.double_(number -> number));
            mapping.properties("extracted_at", property -> property.date(date -> date));
            return mapping;
        });
    }

    static Consumer<CreateIndexRequest.Builder> graphRelations() {
        return request -> request.mappings(mapping -> {
            staged(mapping);
            for (String field : List.of(
                    "document_kind",
                    "relation_id",
                    "source_entity_id",
                    "target_entity_id",
                    "orientation",
                    "contribution_type",
                    "keywords",
                    "extractor_provider",
                    "extractor_model",
                    "prompt_version",
                    "extraction_profile_fingerprint")) {
                mapping.properties(field, property -> property.keyword(keyword -> keyword));
            }
            mapping.properties("description", property -> property.text(text -> text));
            mapping.properties("projection_generation", property -> property.long_(number -> number));
            mapping.properties("weight", property -> property.double_(number -> number));
            mapping.properties("confidence", property -> property.double_(number -> number));
            mapping.properties("extracted_at", property -> property.date(date -> date));
            return mapping;
        });
    }

    static Consumer<CreateIndexRequest.Builder> status() {
        return request -> request.mappings(mapping -> {
            for (String field : List.of(
                    OpenSearchProjectionCodec.ORGANIZATION_ID,
                    OpenSearchProjectionCodec.REVISION_ID,
                    "worker_job_id",
                    "status",
                    "content_sha256",
                    "error_code")) {
                mapping.properties(field, property -> property.keyword(keyword -> keyword));
            }
            mapping.properties("observed_at", property -> property.date(date -> date));
            disabledObject(mapping, "metadata");
            return mapping;
        });
    }

    static Consumer<CreateIndexRequest.Builder> vector(int dimensions) {
        return request -> request
                .settings(settings -> settings
                        .knn(true)
                        .knnAlgoParamEfSearch(100))
                .mappings(mapping -> {
                    staged(mapping);
                    for (String field : List.of(
                            "subject_id",
                            "vector_kind",
                            "embedding_profile_id",
                            "model")) {
                        mapping.properties(
                                field,
                                property -> property.keyword(keyword -> keyword));
                    }
                    mapping.properties("dimensions", property -> property.integer(number -> number));
                    mapping.properties(
                            "vector",
                            property -> property.knnVector(vector -> vector
                                    .dimension(dimensions)
                                    .method(method -> method
                                            .name("hnsw")
                                            .spaceType("cosinesimil")
                                            .engine("lucene")
                                            .parameters(
                                                    "ef_construction",
                                             JsonData.of(200))
                                             .parameters("m", JsonData.of(16)))));
                    disabledObject(mapping, "metadata");
                    return mapping;
                });
    }

    private static void staged(
            org.opensearch.client.opensearch._types.mapping.TypeMapping.Builder mapping) {
        for (String field : STAGED_KEYWORDS) {
            mapping.properties(field, property -> property.keyword(keyword -> keyword));
        }
        mapping.properties(
                OpenSearchProjectionCodec.GENERATION,
                property -> property.long_(number -> number));
        mapping.properties(
                OpenSearchProjectionCodec.ACL_GENERATION,
                property -> property.long_(number -> number));
    }

    private static void disabledObject(
            org.opensearch.client.opensearch._types.mapping.TypeMapping.Builder mapping,
            String field) {
        mapping.properties(
                field,
                property -> property.object(object -> object.enabled(false)));
    }
}
