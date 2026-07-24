package com.orgmemory.graphrag.query;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record KeywordPlan(
        List<String> highLevel,
        List<String> lowLevel,
        Source source) {

    public KeywordPlan {
        highLevel = normalize(highLevel);
        lowLevel = normalize(lowLevel);
        Objects.requireNonNull(source, "source");
    }

    public static KeywordPlan model(List<String> highLevel, List<String> lowLevel) {
        return new KeywordPlan(highLevel, lowLevel, Source.MODEL);
    }

    public static KeywordPlan trusted(List<String> highLevel, List<String> lowLevel) {
        return new KeywordPlan(highLevel, lowLevel, Source.TRUSTED_CALLER);
    }

    public static KeywordPlan empty(Source source) {
        return new KeywordPlan(List.of(), List.of(), source);
    }

    public boolean empty() {
        return highLevel.isEmpty() && lowLevel.isEmpty();
    }

    public String joinedHighLevel() {
        return String.join(", ", highLevel);
    }

    public String joinedLowLevel() {
        return String.join(", ", lowLevel);
    }

    private static List<String> normalize(List<String> values) {
        Objects.requireNonNull(values, "values");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String candidate = value.strip();
            if (!candidate.isEmpty()) {
                normalized.add(candidate);
            }
        }
        return List.copyOf(normalized);
    }

    public enum Source {
        MODEL,
        TRUSTED_CALLER,
        SHORT_QUERY_FALLBACK;

        public String cacheValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
