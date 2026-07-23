package com.orgmemory.worker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.knowledge.ConnectorContractVersions;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorCrawlCheckpointService;
import com.orgmemory.core.knowledge.ConnectorIngestionResult;
import com.orgmemory.core.knowledge.ConnectorIngestionService;
import com.orgmemory.core.knowledge.QueryEmbeddingPort;
import com.orgmemory.core.knowledge.UnsupportedConnectorPayloadException;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Proves the driver's progress outlives the process that made it. The staging runner tracked
 * processed cursors in an in-process set, so a restart re-offered every batch the producer
 * still held; a checkpoint in the database is what makes the second driver skip them.
 *
 * <p>Ingestion is stubbed here on purpose. What is under test is the driver's decision to skip,
 * retry, or give up — not the ledger, which the other connector suites exercise against real
 * data.
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "orgmemory.ingestion.processing.scheduling-enabled=false",
        "orgmemory.ingestion.processing.embedding-provider=fixture",
        "orgmemory.ingestion.processing.embedding-model=fixture-embed",
        "orgmemory.ingestion.processing.embedding-dimensions=3",
        "orgmemory.connector.scheduling-enabled=false"
})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConnectorCrawlCheckpointIntegrationTests {

    private static final UUID ORG = UUID.fromString("e3000000-0000-4000-8000-000000000001");
    private static final UUID SPACE = UUID.fromString("e3000000-0000-4000-8000-000000000003");
    private static final UUID ACTOR = UUID.fromString("e3000000-0000-4000-8000-000000000004");
    private static final String STEADY_CONNECTION = "T-checkpoint-workspace";
    private static final String FLAKY_CONNECTION = "T-flaky-workspace";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    @MockitoBean
    ObjectStoragePort objects;

    @MockitoBean
    EmbeddingModel embeddingModel;

    @MockitoBean
    RelationshipTupleWritePort relationshipTuples;

    @MockitoBean
    RelationshipAuthorizationPort entryAuthorization;

    @MockitoBean
    RelationshipAuthorizationSetPort setAuthorization;

    @MockitoBean
    QueryEmbeddingPort queryEmbeddings;

    @Autowired
    ConnectorCrawlCheckpointService checkpoints;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aRestartedDriverResumesInsteadOfReplaying() {
        seedOrganization();
        List<String> firstRun = new ArrayList<>();
        new ConnectorCrawlRunner(
                        List.of(() -> List.of(batch(STEADY_CONNECTION, "cursor-1"), batch(STEADY_CONNECTION, "cursor-2"))),
                        recordingIngestion(firstRun),
                        checkpoints)
                .runPending();

        assertEquals(List.of("cursor-1", "cursor-2"), firstRun, "a fresh connection ingests both");
        assertEquals(
                "cursor-2",
                checkpoints.lastCompletedCursor(ORG, "slack", STEADY_CONNECTION).orElseThrow(),
                "the checkpoint holds the last cursor the connection completed");

        // A second driver over the same database is what a restart looks like from here.
        List<String> afterRestart = new ArrayList<>();
        new ConnectorCrawlRunner(
                        List.of(() -> List.of(batch(STEADY_CONNECTION, "cursor-2"), batch(STEADY_CONNECTION, "cursor-3"))),
                        recordingIngestion(afterRestart),
                        checkpoints)
                .runPending();

        assertEquals(List.of("cursor-3"), afterRestart, "the completed cursor is not replayed");
        assertEquals("cursor-3", checkpoints.lastCompletedCursor(ORG, "slack", STEADY_CONNECTION).orElseThrow());
    }

    @Test
    void aRejectedBatchIsCheckpointedPastRatherThanRetriedForever() {
        seedOrganization();
        int[] attempts = {0};
        ConnectorIngestionService poisoned = failingIngestion(
                attempts,
                () -> new UnsupportedConnectorPayloadException("Unsupported connector content payload version"));
        ConnectorCrawlRunner driver = new ConnectorCrawlRunner(
                List.of(() -> List.of(batch(STEADY_CONNECTION, "cursor-poison"))), poisoned, checkpoints);

        driver.runPending();
        assertEquals(1, attempts[0], "a rejection that will read the same next time is not retried");

        driver.runPending();
        assertEquals(1, attempts[0], "and the next poll does not offer it again");
    }

    @Test
    void aTransientFailureIsRetriedAndStaysPending() {
        seedOrganization();
        int[] attempts = {0};
        ConnectorIngestionService flaky =
                failingIngestion(attempts, () -> new IllegalStateException("database is unavailable"));
        new ConnectorCrawlRunner(List.of(() -> List.of(batch(FLAKY_CONNECTION, "cursor-flaky"))), flaky, checkpoints)
                .runPending();

        assertEquals(3, attempts[0], "a transient failure is retried within the run");
        assertTrue(
                checkpoints.lastCompletedCursor(ORG, "slack", FLAKY_CONNECTION).isEmpty(),
                "nothing is checkpointed, so the next poll tries again");
    }

    /** Reports every batch as an empty success and records which cursors reached ingestion. */
    private static ConnectorIngestionService recordingIngestion(List<String> seen) {
        ConnectorIngestionService ingestion = mock(ConnectorIngestionService.class);
        when(ingestion.ingest(any())).thenAnswer(invocation -> {
            ConnectorCrawlBatch batch = invocation.getArgument(0);
            seen.add(batch.crawlCursor());
            return new ConnectorIngestionResult(List.of(), List.of(), List.of(), List.of(), List.of());
        });
        return ingestion;
    }

    /** Always throws, counting how many times the driver was willing to ask. */
    private static ConnectorIngestionService failingIngestion(
            int[] attempts, java.util.function.Supplier<RuntimeException> failure) {
        ConnectorIngestionService ingestion = mock(ConnectorIngestionService.class);
        when(ingestion.ingest(any())).thenAnswer(invocation -> {
            attempts[0]++;
            throw failure.get();
        });
        return ingestion;
    }

    private ConnectorCrawlBatch batch(String connectionKey, String cursor) {
        return new ConnectorCrawlBatch(
                ORG,
                "slack",
                connectionKey,
                SPACE,
                ACTOR,
                cursor,
                ConnectorContractVersions.supported(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    @SuppressWarnings("SqlResolve")
    private void seedOrganization() {
        Long existing = jdbc.queryForObject(
                "SELECT count(*) FROM organizations WHERE id = ?", Long.class, ORG);
        if (existing != null && existing > 0) {
            return;
        }
        jdbc.update("INSERT INTO organizations (id, name, created_at, updated_at, version) "
                + "VALUES (?, 'Checkpoint Fixture Org', now(), now(), 0)", ORG);
    }
}
