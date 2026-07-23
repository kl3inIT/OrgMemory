package com.orgmemory.graphrag.parsing;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public record ParserSpec(
        ProcessingComponentRef component,
        Set<String> suffixes,
        boolean userSelectable,
        boolean available,
        String unavailableReason,
        DocumentParser parser) {

    public ParserSpec {
        Objects.requireNonNull(component, "component");
        var normalized = new TreeSet<String>();
        for (String suffix : Objects.requireNonNull(suffixes, "suffixes")) {
            String value = Objects.requireNonNull(suffix, "suffix")
                    .trim()
                    .replaceFirst("^\\.", "")
                    .toLowerCase(Locale.ROOT);
            if (!value.matches("[a-z0-9][a-z0-9.+_-]{0,31}")) {
                throw new IllegalArgumentException("invalid parser suffix " + suffix);
            }
            normalized.add(value);
        }
        suffixes = Set.copyOf(normalized);
        unavailableReason = unavailableReason == null ? "" : unavailableReason.trim();
        if (!available && unavailableReason.isEmpty()) {
            throw new IllegalArgumentException("unavailable parsers require a reason");
        }
        Objects.requireNonNull(parser, "parser");
        if (!component.equals(parser.component())) {
            throw new IllegalArgumentException("parser spec and implementation identity differ");
        }
    }

    public boolean supports(String suffix) {
        return suffixes.contains(suffix.toLowerCase(Locale.ROOT));
    }
}
