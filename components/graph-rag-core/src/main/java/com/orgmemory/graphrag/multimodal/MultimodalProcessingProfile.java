package com.orgmemory.graphrag.multimodal;

import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Immutable policy for sidecar analysis, preflight, and derived chunk construction. */
public record MultimodalProcessingProfile(
        Set<MultimodalModality> enabledModalities,
        Set<MultimodalModality> requiredModalities,
        Map<MultimodalModality, MultimodalAnalysisRoute> routes,
        int leadingContextTokens,
        int trailingContextTokens,
        long maximumImageBytes,
        long maximumImagePixels,
        int chunkTokenBudget,
        String profileSha256) {

    public MultimodalProcessingProfile {
        enabledModalities = immutableEnumSet(enabledModalities);
        requiredModalities = immutableEnumSet(requiredModalities);
        if (!enabledModalities.containsAll(requiredModalities)) {
            throw new IllegalArgumentException("required modalities must also be enabled");
        }
        var routesCopy = new EnumMap<MultimodalModality, MultimodalAnalysisRoute>(
                MultimodalModality.class);
        routesCopy.putAll(Objects.requireNonNull(routes, "routes"));
        if (!routesCopy.keySet().containsAll(enabledModalities)) {
            throw new IllegalArgumentException("every enabled modality requires an analysis route");
        }
        routes = Map.copyOf(routesCopy);
        if (leadingContextTokens < 0 || trailingContextTokens < 0) {
            throw new IllegalArgumentException("context token budgets must not be negative");
        }
        if (maximumImageBytes <= 0 || maximumImagePixels <= 0 || chunkTokenBudget <= 0) {
            throw new IllegalArgumentException("processing limits must be positive");
        }
        String expected = ResolvedDocumentProcessingProfile.sha256(canonicalForm(
                enabledModalities,
                requiredModalities,
                routes,
                leadingContextTokens,
                trailingContextTokens,
                maximumImageBytes,
                maximumImagePixels,
                chunkTokenBudget));
        if (!expected.equals(requireSha256(profileSha256))) {
            throw new IllegalArgumentException(
                    "profileSha256 does not match the multimodal processing profile");
        }
    }

    public static MultimodalProcessingProfile resolve(
            Set<MultimodalModality> enabledModalities,
            Set<MultimodalModality> requiredModalities,
            Map<MultimodalModality, MultimodalAnalysisRoute> routes,
            int leadingContextTokens,
            int trailingContextTokens,
            long maximumImageBytes,
            long maximumImagePixels,
            int chunkTokenBudget) {
        String hash = ResolvedDocumentProcessingProfile.sha256(canonicalForm(
                enabledModalities,
                requiredModalities,
                routes,
                leadingContextTokens,
                trailingContextTokens,
                maximumImageBytes,
                maximumImagePixels,
                chunkTokenBudget));
        return new MultimodalProcessingProfile(
                enabledModalities,
                requiredModalities,
                routes,
                leadingContextTokens,
                trailingContextTokens,
                maximumImageBytes,
                maximumImagePixels,
                chunkTokenBudget,
                hash);
    }

    private static String canonicalForm(
            Set<MultimodalModality> enabled,
            Set<MultimodalModality> required,
            Map<MultimodalModality, MultimodalAnalysisRoute> routes,
            int leading,
            int trailing,
            long maximumBytes,
            long maximumPixels,
            int chunkBudget) {
        var value = new StringBuilder();
        enabled.stream().sorted().forEach(modality ->
                value.append("enabled=").append(modality).append('\n'));
        required.stream().sorted().forEach(modality ->
                value.append("required=").append(modality).append('\n'));
        new TreeMap<>(routes).forEach((modality, route) -> value
                .append("route.")
                .append(modality)
                .append('=')
                .append(route.fingerprint())
                .append('\n'));
        return value.append("context.leading=").append(leading).append('\n')
                .append("context.trailing=").append(trailing).append('\n')
                .append("image.maximumBytes=").append(maximumBytes).append('\n')
                .append("image.maximumPixels=").append(maximumPixels).append('\n')
                .append("chunk.tokenBudget=").append(chunkBudget).append('\n')
                .toString();
    }

    private static Set<MultimodalModality> immutableEnumSet(
            Set<MultimodalModality> values) {
        Objects.requireNonNull(values, "modalities");
        if (values.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(EnumSet.copyOf(values));
    }

    private static String requireSha256(String value) {
        String normalized = Objects.requireNonNull(value, "profileSha256")
                .strip()
                .toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "profileSha256 must be a lowercase SHA-256");
        }
        return normalized;
    }
}
