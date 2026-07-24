package com.orgmemory.worker.graph;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.graph-rag.indexing")
public record GraphIndexingProperties(
        Boolean schedulingEnabled,
        Duration pollInterval,
        String workerId,
        Duration leaseDuration,
        Duration extractionTimeout,
        Integer maximumConcurrency,
        Integer maximumEntitiesPerChunk,
        Integer maximumRelationsPerChunk,
        List<String> entityTypeGuidance,
        List<String> extractionExamples,
        Integer maximumGleaningRounds,
        Integer maximumGleaningInputTokens,
        Integer maximumSectionContextTokens) {

    private static final List<String> DEFAULT_ENTITY_TYPES = List.of(
            "PERSON",
            "ORGANIZATION",
            "TEAM",
            "ROLE",
            "POLICY",
            "PROCESS",
            "SYSTEM",
            "PRODUCT",
            "DOCUMENT",
            "LOCATION",
            "EVENT",
            "CONCEPT",
            "OTHER");

    public GraphIndexingProperties {
        schedulingEnabled = schedulingEnabled == null || schedulingEnabled;
        pollInterval = pollInterval == null ? Duration.ofSeconds(3) : pollInterval;
        workerId = workerId == null || workerId.isBlank()
                ? "graph-worker-" + UUID.randomUUID()
                : workerId.strip();
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(10) : leaseDuration;
        extractionTimeout =
                extractionTimeout == null ? Duration.ofMinutes(2) : extractionTimeout;
        maximumConcurrency = maximumConcurrency == null ? 4 : maximumConcurrency;
        maximumEntitiesPerChunk =
                maximumEntitiesPerChunk == null ? 40 : maximumEntitiesPerChunk;
        maximumRelationsPerChunk =
                maximumRelationsPerChunk == null ? 60 : maximumRelationsPerChunk;
        entityTypeGuidance = entityTypeGuidance == null
                ? DEFAULT_ENTITY_TYPES
                : entityTypeGuidance.stream().map(String::strip).filter(value -> !value.isEmpty()).toList();
        extractionExamples = extractionExamples == null
                ? List.of()
                : extractionExamples.stream().map(String::strip).filter(value -> !value.isEmpty()).toList();
        maximumGleaningRounds = maximumGleaningRounds == null ? 1 : maximumGleaningRounds;
        maximumGleaningInputTokens =
                maximumGleaningInputTokens == null ? 24_000 : maximumGleaningInputTokens;
        maximumSectionContextTokens =
                maximumSectionContextTokens == null ? 256 : maximumSectionContextTokens;
        Assert.isTrue(
                !pollInterval.isNegative() && !pollInterval.isZero(),
                "graph indexing poll interval must be positive");
        Assert.isTrue(
                !leaseDuration.isNegative() && !leaseDuration.isZero(),
                "graph indexing lease duration must be positive");
        Assert.isTrue(
                !extractionTimeout.isNegative() && !extractionTimeout.isZero(),
                "graph extraction timeout must be positive");
        Assert.isTrue(
                maximumConcurrency > 0 && maximumConcurrency <= 32,
                "graph extraction concurrency must be between 1 and 32");
        Assert.isTrue(
                maximumEntitiesPerChunk > 0 && maximumRelationsPerChunk > 0,
                "graph extraction limits must be positive");
        Assert.notEmpty(entityTypeGuidance, "graph entity type guidance must not be empty");
        Assert.isTrue(
                maximumGleaningRounds >= 0 && maximumGleaningRounds <= 1,
                "maximum gleaning rounds must be 0 or 1 for LightRAG v1.5.4 parity");
        Assert.isTrue(
                maximumGleaningInputTokens >= 0,
                "maximum gleaning input tokens must be non-negative");
        Assert.isTrue(
                maximumSectionContextTokens > 0,
                "maximum section context tokens must be positive");
    }
}
