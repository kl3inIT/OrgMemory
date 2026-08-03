package com.orgmemory.core.assetregistry.skilldelivery;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public record SkillReleaseContent(
        SkillReleaseDescriptor descriptor,
        InputStream content) implements AutoCloseable {

    public SkillReleaseContent {
        descriptor = Objects.requireNonNull(descriptor, "descriptor");
        content = Objects.requireNonNull(content, "content");
    }

    @Override
    public void close() throws IOException {
        content.close();
    }
}
