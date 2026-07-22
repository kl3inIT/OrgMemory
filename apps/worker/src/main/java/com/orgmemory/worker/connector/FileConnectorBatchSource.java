package com.orgmemory.worker.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
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

    private final ObjectMapper objectMapper;
    private final ConnectorCrawlProperties properties;

    FileConnectorBatchSource(ObjectMapper objectMapper, ConnectorCrawlProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public List<ConnectorCrawlBatch> pendingBatches() {
        String directory = properties.fixturesDirectory();
        if (directory.isBlank()) {
            return List.of();
        }
        Path root = Path.of(directory);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(root)) {
            return entries
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(this::read)
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list connector fixtures in " + root, exception);
        }
    }

    private ConnectorCrawlBatch read(Path path) {
        try {
            return objectMapper.readValue(Files.readAllBytes(path), ConnectorCrawlBatch.class);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read connector batch " + path, exception);
        }
    }
}
