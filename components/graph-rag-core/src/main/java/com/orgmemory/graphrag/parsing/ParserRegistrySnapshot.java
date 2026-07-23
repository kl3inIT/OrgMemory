package com.orgmemory.graphrag.parsing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable parser table pinned for the lifetime of one processing batch. */
public final class ParserRegistrySnapshot {

    private final Map<String, ParserSpec> specs;

    ParserRegistrySnapshot(Map<String, ParserSpec> specs) {
        this.specs = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(specs));
    }

    public ParserSpec require(String parserId) {
        String key = Objects.requireNonNull(parserId, "parserId").trim().toLowerCase();
        ParserSpec spec = specs.get(key);
        if (spec == null) {
            throw new IllegalArgumentException("unknown parser " + key);
        }
        if (!spec.available()) {
            throw new ParserUnavailableException(key, spec.unavailableReason());
        }
        return spec;
    }

    public ParserSpec route(String suffix, String requestedParserId) {
        if (requestedParserId != null && !requestedParserId.isBlank()) {
            ParserSpec requested = require(requestedParserId);
            if (!requested.supports(suffix)) {
                throw new IllegalArgumentException(
                        "parser " + requested.component().id() + " does not support ." + suffix);
            }
            return requested;
        }
        return specs.values().stream()
                .filter(ParserSpec::userSelectable)
                .filter(ParserSpec::available)
                .filter(spec -> spec.supports(suffix))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "no available parser supports ." + suffix));
    }

    public List<ParserSpec> specs() {
        return List.copyOf(specs.values());
    }
}
