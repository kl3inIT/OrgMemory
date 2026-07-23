package com.orgmemory.worker.connector;

import java.nio.file.Files;
import java.nio.file.Path;

/** Locates the committed connector crawl-batch fixtures relative to the repository root. */
final class ConnectorFixtures {

    private ConnectorFixtures() {
    }

    static Path directory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.isDirectory(candidate.resolve("demo/fixtures/connector"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException(
                    "connector fixtures directory not found from " + Path.of("").toAbsolutePath());
        }
        return candidate.resolve("demo/fixtures/connector");
    }
}
