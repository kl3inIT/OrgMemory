package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.storage.ObjectKey;
import java.util.Objects;

/** Immutable Source Ledger evidence required to open one authorized citation. */
public record SourceCitationEvidence(
        String fileName,
        String mediaType,
        long contentLength,
        String contentSha256,
        ObjectKey objectKey,
        long storedContentLength,
        String storedContentSha256) {

    public SourceCitationEvidence {
        fileName = requireText(fileName, "fileName");
        mediaType = requireText(mediaType, "mediaType");
        contentSha256 = requireText(contentSha256, "contentSha256");
        Objects.requireNonNull(objectKey, "objectKey");
        storedContentSha256 = requireText(
                storedContentSha256, "storedContentSha256");
        if (contentLength < 0) {
            throw new IllegalArgumentException(
                    "contentLength must not be negative");
        }
        if (storedContentLength < 0) {
            throw new IllegalArgumentException(
                    "storedContentLength must not be negative");
        }
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
