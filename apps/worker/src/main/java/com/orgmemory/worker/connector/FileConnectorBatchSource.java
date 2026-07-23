package com.orgmemory.worker.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorPoll;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/**
 * The staging {@link ConnectorBatchSource}: reads committed crawl-batch JSON from a configured
 * directory, in filename order, and deserializes each into a {@link ConnectorCrawlBatch}. This
 * is the fixture-driven producer; the live Slack adapter will implement the same port over the
 * Slack Web API next increment. An unset directory yields no batches, so the driver is inert
 * until a deployment opts in.
 */
@Component
class FileConnectorBatchSource implements ConnectorBatchSource {

    // The connector worker is a non-web application with no shared ObjectMapper bean; the
    // crawl-batch records need no custom configuration, so this source owns a plain mapper.
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ConnectorCrawlProperties properties;

    FileConnectorBatchSource(ConnectorCrawlProperties properties) {
        this.properties = properties;
    }

    /**
     * Files on disk have no per-connection failure to report: a fixture either parses or the
     * whole directory is unreadable, and the second is a failure of the source rather than of
     * one connection. So this always reports an empty unavailable list.
     */
    @Override
    public ConnectorPoll pendingBatches() {
        String directory = properties.fixturesDirectory();
        if (directory.isBlank()) {
            return ConnectorPoll.of(List.of());
        }
        Path root = Path.of(directory);
        if (!Files.isDirectory(root)) {
            return ConnectorPoll.of(List.of());
        }
        try (Stream<Path> entries = Files.list(root)) {
            return ConnectorPoll.of(entries
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::read)
                    .toList());
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list connector fixtures in " + root, exception);
        }
    }

    private ConnectorCrawlBatch read(Path path) {
        try {
            return OBJECT_MAPPER.readValue(Files.readAllBytes(path), ConnectorCrawlBatch.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read connector batch " + path, exception);
        }
    }
}
