package com.orgmemory.graphrag.processing;

import static com.orgmemory.graphrag.validation.TextValidation.requireText;

import com.orgmemory.graphrag.model.ExtractionProfile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable identity of the algorithm that turns one pinned chunk projection into
 * graph contributions and their vector payloads.
 *
 * <p>Embedding geometry is deliberately not part of this profile. Provider, model,
 * dimensions and distance metric remain the independent {@code EmbeddingProfile}
 * coordinate.
 */
public record GraphProcessingProfile(
        int schemaVersion,
        String algorithmVersion,
        ExtractionProfile extractionProfile,
        String promptTemplate,
        String mergeSemanticsVersion,
        String embeddingPayloadFormatVersion,
        String canonicalSha256) {

    public static final int CURRENT_SCHEMA_VERSION = 2;
    private static final int EARLIEST_SCHEMA_VERSION = 1;

    public GraphProcessingProfile {
        if (schemaVersion < EARLIEST_SCHEMA_VERSION
                || schemaVersion > CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported graph processing profile schema: " + schemaVersion);
        }
        algorithmVersion = requireText(algorithmVersion, "algorithmVersion");
        Objects.requireNonNull(extractionProfile, "extractionProfile");
        if (schemaVersion == 1
                && extractionProfile.openAiReasoningEffort() != null) {
            throw new IllegalArgumentException(
                    "schema-v1 graph processing profiles cannot set OpenAI reasoning effort");
        }
        promptTemplate = requireText(promptTemplate, "promptTemplate");
        mergeSemanticsVersion =
                requireText(mergeSemanticsVersion, "mergeSemanticsVersion");
        embeddingPayloadFormatVersion = requireText(
                embeddingPayloadFormatVersion, "embeddingPayloadFormatVersion");
        canonicalSha256 = requireSha256(canonicalSha256);
        String expected = ResolvedDocumentProcessingProfile.sha256(canonicalForm(
                schemaVersion,
                algorithmVersion,
                extractionProfile,
                promptTemplate,
                mergeSemanticsVersion,
                embeddingPayloadFormatVersion));
        if (!expected.equals(canonicalSha256)) {
            throw new IllegalArgumentException(
                    "canonicalSha256 does not match the graph processing profile");
        }
    }

    public static GraphProcessingProfile resolve(
            String algorithmVersion,
            ExtractionProfile extractionProfile,
            String promptTemplate,
            String mergeSemanticsVersion,
            String embeddingPayloadFormatVersion) {
        String canonical = canonicalForm(
                CURRENT_SCHEMA_VERSION,
                algorithmVersion,
                extractionProfile,
                promptTemplate,
                mergeSemanticsVersion,
                embeddingPayloadFormatVersion);
        return new GraphProcessingProfile(
                CURRENT_SCHEMA_VERSION,
                algorithmVersion,
                extractionProfile,
                promptTemplate,
                mergeSemanticsVersion,
                embeddingPayloadFormatVersion,
                ResolvedDocumentProcessingProfile.sha256(canonical));
    }

    public static GraphProcessingProfile restore(
            String canonicalForm,
            String canonicalSha256) {
        Map<String, String> fields = parseCanonicalForm(canonicalForm);
        int schemaVersion = integer(take(fields, "schemaVersion"), "schemaVersion");
        String algorithmVersion = decoded(take(fields, "algorithmVersion"));
        String provider = decoded(take(fields, "extraction.provider"));
        String model = decoded(take(fields, "extraction.model"));
        String openAiReasoningEffort = schemaVersion >= 2
                ? decodedOptional(take(fields, "extraction.openAiReasoningEffort"))
                : null;
        String promptVersion = decoded(take(fields, "extraction.promptVersion"));
        int maxEntities = integer(
                take(fields, "extraction.maxEntities"), "extraction.maxEntities");
        int maxRelations = integer(
                take(fields, "extraction.maxRelations"), "extraction.maxRelations");
        List<String> entityTypes = takeList(fields, "extraction.entityType");
        List<String> examples = takeList(fields, "extraction.example");
        int maxGleaningRounds = integer(
                take(fields, "extraction.maxGleaningRounds"),
                "extraction.maxGleaningRounds");
        int maxGleaningInputTokens = integer(
                take(fields, "extraction.maxGleaningInputTokens"),
                "extraction.maxGleaningInputTokens");
        int maxSectionContextTokens = integer(
                take(fields, "extraction.maxSectionContextTokens"),
                "extraction.maxSectionContextTokens");
        String promptTemplate = decoded(take(fields, "promptTemplate"));
        String mergeSemanticsVersion =
                decoded(take(fields, "mergeSemanticsVersion"));
        String embeddingPayloadFormatVersion =
                decoded(take(fields, "embeddingPayloadFormatVersion"));
        if (!fields.isEmpty()) {
            throw new IllegalArgumentException(
                    "unknown graph processing profile fields: " + fields.keySet());
        }
        return new GraphProcessingProfile(
                schemaVersion,
                algorithmVersion,
                new ExtractionProfile(
                        provider,
                        model,
                        openAiReasoningEffort,
                        promptVersion,
                        maxEntities,
                        maxRelations,
                        entityTypes,
                        examples,
                        maxGleaningRounds,
                        maxGleaningInputTokens,
                        maxSectionContextTokens),
                promptTemplate,
                mergeSemanticsVersion,
                embeddingPayloadFormatVersion,
                canonicalSha256);
    }

    public String canonicalForm() {
        return canonicalForm(
                schemaVersion,
                algorithmVersion,
                extractionProfile,
                promptTemplate,
                mergeSemanticsVersion,
                embeddingPayloadFormatVersion);
    }

    public String extractionFingerprint() {
        return extractionProfile.fingerprint();
    }

    private static String canonicalForm(
            int schemaVersion,
            String algorithmVersion,
            ExtractionProfile extractionProfile,
            String promptTemplate,
            String mergeSemanticsVersion,
            String embeddingPayloadFormatVersion) {
        Objects.requireNonNull(extractionProfile, "extractionProfile");
        StringBuilder value = new StringBuilder()
                .append("schemaVersion=").append(schemaVersion).append('\n')
                .append("algorithmVersion=").append(encoded(algorithmVersion)).append('\n')
                .append("extraction.provider=")
                .append(encoded(extractionProfile.provider()))
                .append('\n')
                .append("extraction.model=")
                .append(encoded(extractionProfile.model()))
                .append('\n')
                .append(schemaVersion >= 2
                        ? "extraction.openAiReasoningEffort="
                                + encodedOptional(extractionProfile.openAiReasoningEffort())
                                + '\n'
                        : "")
                .append("extraction.promptVersion=")
                .append(encoded(extractionProfile.promptVersion()))
                .append('\n')
                .append("extraction.maxEntities=")
                .append(extractionProfile.maxEntities())
                .append('\n')
                .append("extraction.maxRelations=")
                .append(extractionProfile.maxRelations())
                .append('\n');
        appendList(value, "extraction.entityType", extractionProfile.entityTypeGuidance());
        appendList(value, "extraction.example", extractionProfile.examples());
        value.append("extraction.maxGleaningRounds=")
                .append(extractionProfile.maxGleaningRounds())
                .append('\n')
                .append("extraction.maxGleaningInputTokens=")
                .append(extractionProfile.maxGleaningInputTokens())
                .append('\n')
                .append("extraction.maxSectionContextTokens=")
                .append(extractionProfile.maxSectionContextTokens())
                .append('\n')
                .append("promptTemplate=").append(encoded(promptTemplate)).append('\n')
                .append("mergeSemanticsVersion=")
                .append(encoded(mergeSemanticsVersion))
                .append('\n')
                .append("embeddingPayloadFormatVersion=")
                .append(encoded(embeddingPayloadFormatVersion))
                .append('\n');
        return value.toString();
    }

    private static void appendList(
            StringBuilder target,
            String field,
            List<String> values) {
        target.append(field).append(".count=").append(values.size()).append('\n');
        for (int index = 0; index < values.size(); index++) {
            target.append(field)
                    .append('.')
                    .append(index)
                    .append('=')
                    .append(encoded(values.get(index)))
                    .append('\n');
        }
    }

    private static List<String> takeList(
            Map<String, String> fields,
            String field) {
        int count = integer(take(fields, field + ".count"), field + ".count");
        if (count < 0 || count > 10_000) {
            throw new IllegalArgumentException(field + " count is invalid");
        }
        List<String> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(decoded(take(fields, field + "." + index)));
        }
        return List.copyOf(values);
    }

    private static Map<String, String> parseCanonicalForm(String canonicalForm) {
        String normalized = requireText(canonicalForm, "canonicalForm");
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : normalized.split("\\n")) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException(
                        "invalid graph processing profile canonical form");
            }
            String key = line.substring(0, separator);
            String previous = fields.put(key, line.substring(separator + 1));
            if (previous != null) {
                throw new IllegalArgumentException(
                        "duplicate graph processing profile field: " + key);
            }
        }
        return fields;
    }

    private static String take(Map<String, String> fields, String key) {
        String value = fields.remove(key);
        if (value == null) {
            throw new IllegalArgumentException(
                    "missing graph processing profile field: " + key);
        }
        return value;
    }

    private static int integer(String value, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " must be an integer", exception);
        }
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(requireText(value, "profile value")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static String decoded(String value) {
        try {
            return new String(
                    Base64.getUrlDecoder().decode(value),
                    StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "invalid graph processing profile encoding", exception);
        }
    }

    private static String encodedOptional(String value) {
        return value == null ? "" : encoded(value);
    }

    private static String decodedOptional(String value) {
        return value.isEmpty() ? null : decoded(value);
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "canonicalSha256").toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "canonicalSha256 must be lowercase SHA-256 hex");
        }
        return normalized;
    }
}
