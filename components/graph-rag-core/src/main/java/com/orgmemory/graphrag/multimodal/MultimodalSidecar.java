package com.orgmemory.graphrag.multimodal;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Versioned parser interchange contract for multimodal evidence. */
public record MultimodalSidecar(
        String schemaVersion,
        String documentId,
        String documentName,
        String documentFormat,
        String parserComponent,
        String canonicalTextSha256,
        List<MultimodalSidecarItem> items) {

    public static final String SCHEMA_VERSION = "orgmemory.multimodal-sidecar/v1";

    public MultimodalSidecar {
        schemaVersion = requireText(schemaVersion, "schemaVersion");
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("unsupported multimodal sidecar schema");
        }
        documentId = requireText(documentId, "documentId");
        documentName = requireText(documentName, "documentName");
        documentFormat = requireText(documentFormat, "documentFormat");
        parserComponent = requireText(parserComponent, "parserComponent");
        canonicalTextSha256 = requireSha256(canonicalTextSha256);
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        var ids = new HashSet<String>();
        for (MultimodalSidecarItem item : items) {
            Objects.requireNonNull(item, "item");
            if (!ids.add(item.itemId())) {
                throw new IllegalArgumentException("sidecar item ids must be unique");
            }
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private static String requireSha256(String value) {
        String normalized = requireText(value, "canonicalTextSha256").toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                    "canonicalTextSha256 must be a lowercase SHA-256");
        }
        return normalized;
    }
}
