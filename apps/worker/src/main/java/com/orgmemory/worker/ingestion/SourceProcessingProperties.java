package com.orgmemory.worker.ingestion;

import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.ingestion.processing")
public record SourceProcessingProperties(
        String workerId,
        Duration leaseDuration,
        String pipelineVersion,
        String parserId,
        String policyId,
        String tokenizerEncoding,
        String normalizerVersion,
        String embeddingProvider,
        String embeddingModel,
        Integer embeddingDimensions,
        Integer chunkSize,
        Integer chunkOverlap,
        Integer semanticEmbeddingBatchSize,
        Integer maximumChunks,
        Integer maxJobsPerInvocation,
        Duration maxWallClock) {

    public SourceProcessingProperties {
        workerId = workerId == null || workerId.isBlank()
                ? "worker-" + UUID.randomUUID()
                : workerId.strip();
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(5) : leaseDuration;
        pipelineVersion = defaultText(pipelineVersion, "source-pipeline-v1");
        parserId = defaultText(parserId, SpringAiDocumentParser.COMPONENT.id());
        policyId = defaultText(policyId, RequestedProcessingPolicy.STRUCTURED_BLOCK_V1);
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
        maxJobsPerInvocation = maxJobsPerInvocation == null ? 10 : maxJobsPerInvocation;
        maxWallClock = maxWallClock == null ? Duration.ofSeconds(30) : maxWallClock;
        Assert.isTrue(!leaseDuration.isNegative() && !leaseDuration.isZero(), "lease duration must be positive");
        Assert.isTrue(
                embeddingDimensions > 0 && embeddingDimensions <= 16000,
                "embedding dimensions must be between 1 and 16000");
        Assert.isTrue(chunkSize > 0, "chunkSize must be positive");
        Assert.isTrue(chunkOverlap >= 0, "chunkOverlap must not be negative");
        Assert.isTrue(chunkOverlap < chunkSize, "chunkOverlap must be less than chunkSize");
        Assert.isTrue(
                semanticEmbeddingBatchSize > 0,
                "semanticEmbeddingBatchSize must be positive");
        Assert.isTrue(maximumChunks > 0, "maximumChunks must be positive");
        Assert.isTrue(
                maximumChunks < Integer.MAX_VALUE,
                "maximumChunks must allow a sentinel chunk");
        Assert.isTrue(
                maxJobsPerInvocation > 0 && maxJobsPerInvocation <= 100,
                "maxJobsPerInvocation must be between 1 and 100");
        Assert.isTrue(
                !maxWallClock.isNegative()
                        && !maxWallClock.isZero()
                        && maxWallClock.compareTo(Duration.ofMinutes(10)) <= 0,
                "maxWallClock must be between zero and ten minutes");
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }
}
