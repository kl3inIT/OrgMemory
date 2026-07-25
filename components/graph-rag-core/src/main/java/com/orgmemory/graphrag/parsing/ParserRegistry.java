package com.orgmemory.graphrag.parsing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;

/** Mutable startup registry. Processing code consumes only immutable snapshots. */
public final class ParserRegistry {

    private final Map<String, ParserSpec> specs = new LinkedHashMap<>();

    public ParserRegistry register(ParserSpec spec) {
        Objects.requireNonNull(spec, "spec");
        ParserSpec previous = specs.putIfAbsent(spec.component().id(), spec);
        if (previous != null) {
            throw new IllegalArgumentException(
                    "parser already registered: " + spec.component().id());
        }
        return this;
    }

    public ParserRegistry loadPlugins(ClassLoader classLoader) {
        ServiceLoader.load(ParserPlugin.class, Objects.requireNonNull(classLoader, "classLoader"))
                .stream()
                .map(ServiceLoader.Provider::get)
                .forEach(plugin -> plugin.register(this));
        return this;
    }

    public ParserRegistrySnapshot snapshot() {
        return new ParserRegistrySnapshot(specs);
    }
}
