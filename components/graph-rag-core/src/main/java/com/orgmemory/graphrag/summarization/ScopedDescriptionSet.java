package com.orgmemory.graphrag.summarization;

import static com.orgmemory.graphrag.validation.TextValidation.requireText;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ScopedDescriptionSet(
        UUID canonicalId,
        String subjectKind,
        String subjectName,
        List<String> descriptions,
        String authorizationFingerprint,
        String projectionFingerprint) {

    public ScopedDescriptionSet {
        Objects.requireNonNull(canonicalId, "canonicalId");
        subjectKind = requireText(subjectKind, "subjectKind");
        subjectName = requireText(subjectName, "subjectName");
        descriptions = Objects.requireNonNull(descriptions, "descriptions").stream()
                .map(description -> requireText(description, "description"))
                .distinct()
                .toList();
        if (descriptions.isEmpty()) {
            throw new IllegalArgumentException("descriptions must not be empty");
        }
        authorizationFingerprint =
                requireText(authorizationFingerprint, "authorizationFingerprint");
        projectionFingerprint =
                requireText(projectionFingerprint, "projectionFingerprint");
    }

}
