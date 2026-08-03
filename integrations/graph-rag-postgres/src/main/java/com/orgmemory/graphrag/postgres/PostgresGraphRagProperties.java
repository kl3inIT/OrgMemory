package com.orgmemory.graphrag.postgres;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "orgmemory.graph-rag.postgres",
        ignoreUnknownFields = false)
public class PostgresGraphRagProperties {

    private boolean enabled = true;
    private boolean provisionIndexes = true;
    private boolean reconcilePublishedBatches = false;
    private int reconciliationPageSize = 100;
    private int reconciliationMaximumBatches = 1000;
    private long reconciliationMaximumEntities = 1_000_000;
    private long reconciliationMaximumRelationContributions = 1_000_000;
    private PostgresGraphTopologyBackend topologyBackend =
            PostgresGraphTopologyBackend.APACHE_AGE;
    private PostgresVectorIndexStrategy vectorIndexStrategy =
            PostgresVectorIndexStrategy.HNSW;
    private Set<Integer> indexedVectorDimensions = new LinkedHashSet<>(Set.of(1536));
    private int hnswM = 16;
    private int hnswEfConstruction = 64;
    private int ivfFlatLists = 100;
    private int writeBatchSize = PostgresBatchOperations.DEFAULT_BATCH_SIZE;
    private String vchordBuildOptions = "";

    public PostgresGraphStoreOptions toStoreOptions() {
        return new PostgresGraphStoreOptions(
                vectorIndexStrategy,
                indexedVectorDimensions,
                hnswM,
                hnswEfConstruction,
                ivfFlatLists,
                vchordBuildOptions);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isProvisionIndexes() {
        return provisionIndexes;
    }

    public void setProvisionIndexes(boolean provisionIndexes) {
        this.provisionIndexes = provisionIndexes;
    }

    public boolean isReconcilePublishedBatches() {
        return reconcilePublishedBatches;
    }

    public void setReconcilePublishedBatches(boolean reconcilePublishedBatches) {
        this.reconcilePublishedBatches = reconcilePublishedBatches;
    }

    public int getReconciliationPageSize() {
        return reconciliationPageSize;
    }

    public void setReconciliationPageSize(int reconciliationPageSize) {
        if (reconciliationPageSize < 1 || reconciliationPageSize > 500) {
            throw new IllegalArgumentException(
                    "reconciliationPageSize must be between 1 and 500");
        }
        this.reconciliationPageSize = reconciliationPageSize;
    }

    public int getReconciliationMaximumBatches() {
        return reconciliationMaximumBatches;
    }

    public void setReconciliationMaximumBatches(int reconciliationMaximumBatches) {
        if (reconciliationMaximumBatches < 1 || reconciliationMaximumBatches > 10_000) {
            throw new IllegalArgumentException(
                    "reconciliationMaximumBatches must be between 1 and 10000");
        }
        this.reconciliationMaximumBatches = reconciliationMaximumBatches;
    }

    public long getReconciliationMaximumEntities() {
        return reconciliationMaximumEntities;
    }

    public void setReconciliationMaximumEntities(long reconciliationMaximumEntities) {
        if (reconciliationMaximumEntities < 1) {
            throw new IllegalArgumentException(
                    "reconciliationMaximumEntities must be positive");
        }
        this.reconciliationMaximumEntities = reconciliationMaximumEntities;
    }

    public long getReconciliationMaximumRelationContributions() {
        return reconciliationMaximumRelationContributions;
    }

    public void setReconciliationMaximumRelationContributions(
            long reconciliationMaximumRelationContributions) {
        if (reconciliationMaximumRelationContributions < 1) {
            throw new IllegalArgumentException(
                    "reconciliationMaximumRelationContributions must be positive");
        }
        this.reconciliationMaximumRelationContributions =
                reconciliationMaximumRelationContributions;
    }

    public PostgresGraphTopologyBackend getTopologyBackend() {
        return topologyBackend;
    }

    public void setTopologyBackend(PostgresGraphTopologyBackend topologyBackend) {
        this.topologyBackend = topologyBackend;
    }

    public PostgresVectorIndexStrategy getVectorIndexStrategy() {
        return vectorIndexStrategy;
    }

    public void setVectorIndexStrategy(PostgresVectorIndexStrategy vectorIndexStrategy) {
        this.vectorIndexStrategy = vectorIndexStrategy;
    }

    public Set<Integer> getIndexedVectorDimensions() {
        return indexedVectorDimensions;
    }

    public void setIndexedVectorDimensions(Set<Integer> indexedVectorDimensions) {
        this.indexedVectorDimensions = indexedVectorDimensions;
    }

    public int getHnswM() {
        return hnswM;
    }

    public void setHnswM(int hnswM) {
        this.hnswM = hnswM;
    }

    public int getHnswEfConstruction() {
        return hnswEfConstruction;
    }

    public void setHnswEfConstruction(int hnswEfConstruction) {
        this.hnswEfConstruction = hnswEfConstruction;
    }

    public int getIvfFlatLists() {
        return ivfFlatLists;
    }

    public void setIvfFlatLists(int ivfFlatLists) {
        this.ivfFlatLists = ivfFlatLists;
    }

    public int getWriteBatchSize() {
        return writeBatchSize;
    }

    public void setWriteBatchSize(int writeBatchSize) {
        if (writeBatchSize < 1 || writeBatchSize > PostgresBatchOperations.DEFAULT_BATCH_SIZE) {
            throw new IllegalArgumentException("writeBatchSize must be between 1 and 500");
        }
        this.writeBatchSize = writeBatchSize;
    }

    public String getVchordBuildOptions() {
        return vchordBuildOptions;
    }

    public void setVchordBuildOptions(String vchordBuildOptions) {
        this.vchordBuildOptions = vchordBuildOptions;
    }
}
