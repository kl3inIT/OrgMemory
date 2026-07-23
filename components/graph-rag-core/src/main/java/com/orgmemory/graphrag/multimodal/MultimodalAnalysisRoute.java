package com.orgmemory.graphrag.multimodal;

import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.util.Objects;

/** Immutable provider route recorded with a multimodal model invocation. */
public record MultimodalAnalysisRoute(
        String provider,
        String model,
        String modelVersion,
        String promptVersion,
        MultimodalAnalyzerRole role,
        double temperature,
        int maxOutputTokens) {

    public MultimodalAnalysisRoute {
        provider = requireText(provider, "provider");
        model = requireText(model, "model");
        modelVersion = requireText(modelVersion, "modelVersion");
        promptVersion = requireText(promptVersion, "promptVersion");
        Objects.requireNonNull(role, "role");
        if (!Double.isFinite(temperature) || temperature < 0.0 || temperature > 2.0) {
            throw new IllegalArgumentException("temperature must be between 0 and 2");
        }
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be positive");
        }
    }

    public String fingerprint() {
        return ResolvedDocumentProcessingProfile.sha256(
                MultimodalFingerprintInput.frame(
                        provider,
                        model,
                        modelVersion,
                        promptVersion,
                        role.name(),
                        Double.toString(temperature),
                        Integer.toString(maxOutputTokens)));
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
