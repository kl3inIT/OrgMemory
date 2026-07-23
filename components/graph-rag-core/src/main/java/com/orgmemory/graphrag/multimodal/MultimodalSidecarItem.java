package com.orgmemory.graphrag.multimodal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** One image, table, or equation anchored to an exact canonical-text span. */
public record MultimodalSidecarItem(
        String itemId,
        MultimodalModality modality,
        int blockIndex,
        int targetStartChar,
        int targetEndChar,
        List<String> headingPath,
        String caption,
        String footnotes,
        MultimodalPayload payload,
        Map<String, String> attributes) {

    public MultimodalSidecarItem {
        itemId = requireText(itemId, "itemId");
        Objects.requireNonNull(modality, "modality");
        if (blockIndex < 0) {
            throw new IllegalArgumentException("blockIndex must not be negative");
        }
        if (targetStartChar < 0 || targetEndChar <= targetStartChar) {
            throw new IllegalArgumentException("target span must be non-empty");
        }
        headingPath = List.copyOf(Objects.requireNonNull(headingPath, "headingPath"));
        caption = Objects.requireNonNullElse(caption, "").strip();
        footnotes = Objects.requireNonNullElse(footnotes, "").strip();
        Objects.requireNonNull(payload, "payload");
        if (payload instanceof MultimodalPayload.Image
                && modality != MultimodalModality.IMAGE
                || payload instanceof MultimodalPayload.Table
                        && modality != MultimodalModality.TABLE
                || payload instanceof MultimodalPayload.Equation
                        && modality != MultimodalModality.EQUATION) {
            throw new IllegalArgumentException("payload does not match modality");
        }
        attributes = Map.copyOf(new TreeMap<>(
                Objects.requireNonNull(attributes, "attributes")));
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
