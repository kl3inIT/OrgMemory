package com.orgmemory.graphrag.model;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public record ExtractionProfile(
        String provider,
        String model,
        String promptVersion,
        int maxEntities,
        int maxRelations,
        List<String> entityTypeGuidance,
        List<String> examples,
        int maxGleaningRounds,
        int maxGleaningInputTokens,
        int maxSectionContextTokens) {

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

    public ExtractionProfile {
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        promptVersion = requireText(promptVersion, "promptVersion");
        if (maxEntities <= 0 || maxRelations <= 0) {
            throw new IllegalArgumentException("extraction limits must be positive");
        }
        entityTypeGuidance = normalizedValues(entityTypeGuidance, "entityTypeGuidance");
        if (entityTypeGuidance.isEmpty()) {
            throw new IllegalArgumentException("entityTypeGuidance must not be empty");
        }
        examples = normalizedValues(examples, "examples");
        if (maxGleaningRounds < 0 || maxGleaningRounds > 1) {
            throw new IllegalArgumentException(
                    "maxGleaningRounds must be 0 or 1 for LightRAG v1.5.4 parity");
        }
        if (maxGleaningInputTokens < 0) {
            throw new IllegalArgumentException(
                    "maxGleaningInputTokens must be non-negative");
        }
        if (maxSectionContextTokens <= 0) {
            throw new IllegalArgumentException(
                    "maxSectionContextTokens must be positive");
        }
    }

    public ExtractionProfile(
            String provider,
            String model,
            String promptVersion,
            int maxEntities,
            int maxRelations,
            List<String> entityTypeGuidance,
            List<String> examples,
            int maxGleaningRounds,
            int maxGleaningInputTokens) {
        this(
                provider,
                model,
                promptVersion,
                maxEntities,
                maxRelations,
                entityTypeGuidance,
                examples,
                maxGleaningRounds,
                maxGleaningInputTokens,
                256);
    }

    public ExtractionProfile(
            String provider,
            String model,
            String promptVersion,
            int maxEntities,
            int maxRelations) {
        this(
                provider,
                model,
                promptVersion,
                maxEntities,
                maxRelations,
                DEFAULT_ENTITY_TYPES,
                List.of(),
                1,
                24_000,
                256);
    }

    public int maximumFinalEntities() {
        return Math.multiplyExact(maxEntities, maxGleaningRounds + 1);
    }

    public int maximumFinalRelations() {
        return Math.multiplyExact(maxRelations, maxGleaningRounds + 1);
    }

    public String fingerprint() {
        MessageDigest digest = sha256();
        update(digest, provider);
        update(digest, model);
        update(digest, promptVersion);
        update(digest, Integer.toString(maxEntities));
        update(digest, Integer.toString(maxRelations));
        entityTypeGuidance.forEach(value -> update(digest, value));
        update(digest, "examples");
        examples.forEach(value -> update(digest, value));
        update(digest, Integer.toString(maxGleaningRounds));
        update(digest, Integer.toString(maxGleaningInputTokens));
        update(digest, Integer.toString(maxSectionContextTokens));
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static List<String> normalizedValues(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        return values.stream()
                .map(value -> requireText(value, field + " value"))
                .distinct()
                .toList();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
