package com.orgmemory.graphrag.summarization;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record DescriptionSummaryRequest(
        String subjectKind,
        String subjectName,
        List<String> descriptions,
        Locale language,
        int maximumOutputTokens,
        String authorizationFingerprint,
        String projectionFingerprint) {

    public DescriptionSummaryRequest {
        subjectKind = requireText(subjectKind, "subjectKind");
        subjectName = requireText(subjectName, "subjectName");
        descriptions = Objects.requireNonNull(descriptions, "descriptions").stream()
                .map(description -> requireText(description, "description"))
                .toList();
        if (descriptions.size() < 2) {
            throw new IllegalArgumentException(
                    "model summary requests require at least two descriptions");
        }
        Objects.requireNonNull(language, "language");
        if (maximumOutputTokens <= 0) {
            throw new IllegalArgumentException("maximumOutputTokens must be positive");
        }
        authorizationFingerprint =
                requireText(authorizationFingerprint, "authorizationFingerprint");
        projectionFingerprint =
                requireText(projectionFingerprint, "projectionFingerprint");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
