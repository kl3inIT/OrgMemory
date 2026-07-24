package com.orgmemory.core.knowledge;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Semantic GraphRAG settings shared by every process that can enqueue or execute
 * a graph indexing job.
 */
@ConfigurationProperties("orgmemory.graph-rag.processing")
public record GraphProcessingProperties(
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

    public GraphProcessingProperties {
        maximumEntitiesPerChunk =
                maximumEntitiesPerChunk == null ? 40 : maximumEntitiesPerChunk;
        maximumRelationsPerChunk =
                maximumRelationsPerChunk == null ? 60 : maximumRelationsPerChunk;
        entityTypeGuidance = normalized(
                entityTypeGuidance == null ? DEFAULT_ENTITY_TYPES : entityTypeGuidance);
        extractionExamples =
                normalized(extractionExamples == null ? List.of() : extractionExamples);
        maximumGleaningRounds =
                maximumGleaningRounds == null ? 1 : maximumGleaningRounds;
        maximumGleaningInputTokens =
                maximumGleaningInputTokens == null ? 24_000 : maximumGleaningInputTokens;
        maximumSectionContextTokens =
                maximumSectionContextTokens == null ? 256 : maximumSectionContextTokens;
        if (maximumEntitiesPerChunk <= 0 || maximumRelationsPerChunk <= 0) {
            throw new IllegalArgumentException(
                    "graph extraction limits must be positive");
        }
        if (entityTypeGuidance.isEmpty()) {
            throw new IllegalArgumentException(
                    "graph entity type guidance must not be empty");
        }
        if (maximumGleaningRounds < 0 || maximumGleaningRounds > 1) {
            throw new IllegalArgumentException(
                    "maximum gleaning rounds must be 0 or 1 for LightRAG v1.5.4 parity");
        }
        if (maximumGleaningInputTokens < 0) {
            throw new IllegalArgumentException(
                    "maximum gleaning input tokens must be non-negative");
        }
        if (maximumSectionContextTokens <= 0) {
            throw new IllegalArgumentException(
                    "maximum section context tokens must be positive");
        }
    }

    private static List<String> normalized(List<String> values) {
        return values.stream()
                .map(value -> value == null ? "" : value.strip())
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
