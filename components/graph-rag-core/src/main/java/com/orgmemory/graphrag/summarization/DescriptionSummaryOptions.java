package com.orgmemory.graphrag.summarization;

import java.util.Locale;
import java.util.Objects;

public record DescriptionSummaryOptions(
        int contextTokenLimit,
        int maximumOutputTokens,
        int forceModelAtFragmentCount,
        String separator,
        Locale language) {

    public DescriptionSummaryOptions {
        if (contextTokenLimit <= 0 || maximumOutputTokens <= 0) {
            throw new IllegalArgumentException("summary token limits must be positive");
        }
        if (forceModelAtFragmentCount < 2) {
            throw new IllegalArgumentException(
                    "forceModelAtFragmentCount must be at least 2");
        }
        separator = Objects.requireNonNull(separator, "separator");
        if (separator.isEmpty()) {
            throw new IllegalArgumentException("separator must not be empty");
        }
        Objects.requireNonNull(language, "language");
    }
}
