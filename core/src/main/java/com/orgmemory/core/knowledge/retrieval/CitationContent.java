package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.storage.ObjectContent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/**
 * Authenticated source evidence opened for one citation response.
 *
 * <p>The object-store key is deliberately not exposed. Callers must close this
 * value after streaming the content.
 */
public record CitationContent(
        UUID chunkId,
        String fileName,
        String mediaType,
        long contentLength,
        String contentSha256,
        ObjectContent object)
        implements AutoCloseable {

    public CitationContent {
        Objects.requireNonNull(chunkId, "chunkId");
        fileName = requireText(fileName, "fileName");
        mediaType = requireText(mediaType, "mediaType");
        contentSha256 = requireText(contentSha256, "contentSha256");
        Objects.requireNonNull(object, "object");
        if (contentLength < 0) {
            throw new IllegalArgumentException(
                    "contentLength must not be negative");
        }
    }

    public InputStream stream() {
        return object.stream();
    }

    @Override
    public void close() throws IOException {
        object.close();
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(
                    field + " must not be blank");
        }
        return normalized;
    }
}
