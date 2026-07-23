package com.orgmemory.integrations.graphrag.sidecar;

import java.util.Objects;
import java.util.Optional;

/** In-memory representation of one LightRAG 1.0 split sidecar bundle. */
public record LightRagSidecarBundle(
        String blocksJsonl,
        Optional<String> drawingsJson,
        Optional<String> tablesJson,
        Optional<String> equationsJson) {

    public LightRagSidecarBundle {
        blocksJsonl = requireText(blocksJsonl, "blocksJsonl");
        drawingsJson = Objects.requireNonNull(drawingsJson, "drawingsJson");
        tablesJson = Objects.requireNonNull(tablesJson, "tablesJson");
        equationsJson = Objects.requireNonNull(equationsJson, "equationsJson");
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
