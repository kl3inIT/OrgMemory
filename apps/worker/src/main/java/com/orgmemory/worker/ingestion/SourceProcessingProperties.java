package com.orgmemory.worker.ingestion;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.ingestion.processing")
public record SourceProcessingProperties(
        Boolean schedulingEnabled,
        Duration pollInterval,
        String workerId,
        Duration leaseDuration,
        String pipelineVersion,
        String parserId,
        String chunkerId,
        String tokenizerEncoding,
        String normalizerVersion,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimensions,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer semanticEmbeddingBatchSize,
        Integer maximumChunks) {

    public SourceProcessingProperties {
        schedulingEnabled = schedulingEnabled == null || schedulingEnabled;
        pollInterval = pollInterval == null ? Duration.ofSeconds(2) : pollInterval;
        workerId = workerId == null || workerId.isBlank()
                ? "worker-" + UUID.randomUUID()
                : workerId.strip();
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(5) : leaseDuration;
        pipelineVersion = defaultText(pipelineVersion, "source-pipeline-v1");
        parserId = defaultText(parserId, "legacy");
        chunkerId = defaultText(chunkerId, "fixed-token");
        tokenizerEncoding = defaultText(tokenizerEncoding, "o200k_base");
        normalizerVersion = defaultText(normalizerVersion, "source-normalizer-v1");
        embeddingProvider = defaultText(embeddingProvider, "openai");
        embeddingModel = defaultText(embeddingModel, "text-embedding-3-large");
        embeddingDimensions = embeddingDimensions == null ? 1536 : embeddingDimensions;
        chunkSize = chunkSize == null ? 800 : chunkSize;
        chunkOverlap = chunkOverlap == null ? 100 : chunkOverlap;
        semanticEmbeddingBatchSize =
                semanticEmbeddingBatchSize == null ? 64 : semanticEmbeddingBatchSize;
        maximumChunks = maximumChunks == null ? 500 : maximumChunks;
        Assert.isTrue(!pollInterval.isNegative() && !pollInterval.isZero(), "poll interval must be positive");
        Assert.isTrue(!leaseDuration.isNegative() && !leaseDuration.isZero(), "lease duration must be positive");
        Assert.isTrue(
                embeddingDimensions > 0 && embeddingDimensions <= 16000,
                "embedding dimensions must be between 1 and 16000");
        Assert.isTrue(
                chunkSize > 0
                        && chunkOverlap >= 0
                        && chunkOverlap < chunkSize
                        && semanticEmbeddingBatchSize > 0
                        && maximumChunks > 0
                        && maximumChunks < Integer.MAX_VALUE,
                "chunk settings must be positive and maximumChunks must allow a sentinel chunk");
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
