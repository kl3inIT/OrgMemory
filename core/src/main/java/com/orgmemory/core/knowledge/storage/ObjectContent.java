package com.orgmemory.core.knowledge.storage;

import java.io.IOException;
import java.io.InputStream;

public record ObjectContent(InputStream stream, StoredObject metadata) implements AutoCloseable {

    public ObjectContent {
        if (stream == null || metadata == null) {
            throw new IllegalArgumentException("object content and metadata are required");
        }
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
