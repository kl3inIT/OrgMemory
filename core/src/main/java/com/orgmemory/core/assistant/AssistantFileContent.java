package com.orgmemory.core.assistant;

import java.io.IOException;
import java.io.InputStream;

public record AssistantFileContent(
        InputStream stream,
        String fileName,
        String mediaType,
        long contentLength,
        boolean inlinePreviewAllowed) implements AutoCloseable {

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
