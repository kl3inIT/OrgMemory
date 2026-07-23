package com.orgmemory.graphrag.processing;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Stable identity of one parser, tokenizer, chunker, or model implementation. */
public record ProcessingComponentRef(String id, String version) {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9._-]{0,127}");

    public ProcessingComponentRef {
        id = normalize(id, "id");
        version = normalize(version, "version");
    }

    private static String normalize(String value, String field) {
        String normalized = Objects.requireNonNull(value, field)
                .trim()
                .toLowerCase(Locale.ROOT);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(
                    field + " must match " + IDENTIFIER.pattern());
        }
        return normalized;
    }

    @Override
    public String toString() {
        return id + "@" + version;
    }
}
