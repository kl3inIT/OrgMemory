package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.storage.ObjectContent;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.UUID;

/** Permission-verified original evidence for one current source revision. */
public record SourceContent(
        UUID sourceId,
        String fileName,
        String mediaType,
        long contentLength,
        String contentSha256,
        ObjectContent object)
        implements AutoCloseable {

    public SourceContent {
        Objects.requireNonNull(sourceId, "sourceId");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(mediaType, "mediaType");
        Objects.requireNonNull(contentSha256, "contentSha256");
        Objects.requireNonNull(object, "object");
        if (contentLength < 0) {
            throw new IllegalArgumentException("contentLength must not be negative");
        }
    }

    public InputStream stream() {
        return object.stream();
    }

    @Override
    public void close() throws IOException {
        object.close();
    }
}
