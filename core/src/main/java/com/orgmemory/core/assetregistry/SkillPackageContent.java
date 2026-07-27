package com.orgmemory.core.assetregistry;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Verified package bytes for one exact Skill release.
 */
public record SkillPackageContent(
        SkillInstallManifest manifest,
        String fileName,
        InputStream stream)
        implements AutoCloseable {

    public SkillPackageContent {
        manifest = Objects.requireNonNull(manifest, "manifest");
        fileName = Objects.requireNonNull(fileName, "fileName");
        stream = Objects.requireNonNull(stream, "stream");
    }

    @Override
    public void close() throws IOException {
        stream.close();
    }
}
