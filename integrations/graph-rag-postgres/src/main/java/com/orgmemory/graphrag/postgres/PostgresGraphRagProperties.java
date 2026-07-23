package com.orgmemory.graphrag.postgres;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.graph-rag.postgres")
public class PostgresGraphRagProperties {

    private boolean enabled = true;
    private ApacheAgeMode apacheAgeMode = ApacheAgeMode.REQUIRED;
    private int maxBatchRecords = 200;
    private long maxBatchPayloadBytes = 4L * 1024 * 1024;
    private PostgresVectorIndexStrategy vectorIndexStrategy =
            PostgresVectorIndexStrategy.HNSW;
    private Set<Integer> indexedVectorDimensions = new LinkedHashSet<>(Set.of(1536));
    private int hnswM = 16;
    private int hnswEfConstruction = 64;
    private int ivfFlatLists = 100;
    private String vchordBuildOptions = "";

    public PostgresGraphStoreOptions toStoreOptions() {
        return new PostgresGraphStoreOptions(
                apacheAgeMode,
                maxBatchRecords,
                maxBatchPayloadBytes,
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

    public ApacheAgeMode getApacheAgeMode() {
        return apacheAgeMode;
    }

    public void setApacheAgeMode(ApacheAgeMode apacheAgeMode) {
        this.apacheAgeMode = apacheAgeMode;
    }

    public int getMaxBatchRecords() {
        return maxBatchRecords;
    }

    public void setMaxBatchRecords(int maxBatchRecords) {
        this.maxBatchRecords = maxBatchRecords;
    }

    public long getMaxBatchPayloadBytes() {
        return maxBatchPayloadBytes;
    }

    public void setMaxBatchPayloadBytes(long maxBatchPayloadBytes) {
        this.maxBatchPayloadBytes = maxBatchPayloadBytes;
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

    public String getVchordBuildOptions() {
        return vchordBuildOptions;
    }

    public void setVchordBuildOptions(String vchordBuildOptions) {
        this.vchordBuildOptions = vchordBuildOptions;
    }
}
