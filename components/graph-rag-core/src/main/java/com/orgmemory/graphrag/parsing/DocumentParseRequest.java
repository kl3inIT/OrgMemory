package com.orgmemory.graphrag.parsing;

import java.util.Objects;
import java.util.Optional;

public record DocumentParseRequest(
        String fileName,
        String mediaType,
        byte[] content,
        Optional<CanonicalDocument> reusableDocument) {

    public DocumentParseRequest {
        fileName = requireText(fileName, "fileName");
        mediaType = requireText(mediaType, "mediaType");
        content = Objects.requireNonNull(content, "content").clone();
        reusableDocument = Objects.requireNonNull(reusableDocument, "reusableDocument");
    }

    @Override
    public byte[] content() {
        return content.clone();
    }

    public String suffix() {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(java.util.Locale.ROOT);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
