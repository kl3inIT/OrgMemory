package com.orgmemory.graphrag.postgres;

import java.util.Objects;
import java.util.Set;

/**
 * Operational options for the PostgreSQL GraphRAG projection.
 *
 * <p>These options affect storage mechanics only. Tenant and authorization scope
 * always come from the use-case ports and cannot be relaxed here.
 */
public record PostgresGraphStoreOptions(
        ApacheAgeMode apacheAgeMode,
        int maxBatchRecords,
        long maxBatchPayloadBytes,
        PostgresVectorIndexStrategy vectorIndexStrategy,
        Set<Integer> indexedVectorDimensions,
        int hnswM,
        int hnswEfConstruction,
        int ivfFlatLists,
        String vchordBuildOptions) {

    private static final int POSTGRES_IDENTIFIER_SAFE_MAX_DIMENSION = 16_000;

    public PostgresGraphStoreOptions {
        Objects.requireNonNull(apacheAgeMode, "apacheAgeMode");
        Objects.requireNonNull(vectorIndexStrategy, "vectorIndexStrategy");
        indexedVectorDimensions = Set.copyOf(
                Objects.requireNonNull(indexedVectorDimensions, "indexedVectorDimensions"));
        if (maxBatchRecords < 1 || maxBatchRecords > 10_000) {
            throw new IllegalArgumentException("maxBatchRecords must be between 1 and 10000");
        }
        if (maxBatchPayloadBytes < 1 || maxBatchPayloadBytes > 64L * 1024 * 1024) {
            throw new IllegalArgumentException(
                    "maxBatchPayloadBytes must be between 1 byte and 64 MiB");
        }
        if (indexedVectorDimensions.stream()
                .anyMatch(dimension -> dimension == null
                        || dimension < 1
                        || dimension > POSTGRES_IDENTIFIER_SAFE_MAX_DIMENSION)) {
            throw new IllegalArgumentException(
                    "indexed vector dimensions must be between 1 and 16000");
        }
        if (hnswM < 2 || hnswM > 100) {
            throw new IllegalArgumentException("hnswM must be between 2 and 100");
        }
        if (hnswEfConstruction < 4 || hnswEfConstruction > 2_000) {
            throw new IllegalArgumentException(
                    "hnswEfConstruction must be between 4 and 2000");
        }
        if (ivfFlatLists < 1 || ivfFlatLists > 1_000_000) {
            throw new IllegalArgumentException("ivfFlatLists must be between 1 and 1000000");
        }
        vchordBuildOptions = vchordBuildOptions == null ? "" : vchordBuildOptions.trim();
        if (vectorIndexStrategy != PostgresVectorIndexStrategy.EXACT
                && indexedVectorDimensions.isEmpty()) {
            throw new IllegalArgumentException(
                    "indexedVectorDimensions is required for approximate vector indexes");
        }
    }

    public static PostgresGraphStoreOptions defaults() {
        return new PostgresGraphStoreOptions(
                ApacheAgeMode.OPTIONAL,
                200,
                4L * 1024 * 1024,
                PostgresVectorIndexStrategy.HNSW,
                Set.of(1536),
                16,
                64,
                100,
                "");
    }

    public PostgresGraphStoreOptions withApacheAgeMode(ApacheAgeMode mode) {
        return new PostgresGraphStoreOptions(
                mode,
                maxBatchRecords,
                maxBatchPayloadBytes,
                vectorIndexStrategy,
                indexedVectorDimensions,
                hnswM,
                hnswEfConstruction,
                ivfFlatLists,
                vchordBuildOptions);
    }
}
