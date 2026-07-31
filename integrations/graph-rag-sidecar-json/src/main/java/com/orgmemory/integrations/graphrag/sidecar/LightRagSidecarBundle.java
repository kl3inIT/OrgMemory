package com.orgmemory.integrations.graphrag.sidecar;

import static com.orgmemory.graphrag.validation.TextValidation.requireText;

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

}
