package com.orgmemory.worker.connector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.knowledge.ConnectorConnectionFailure;
import com.orgmemory.core.knowledge.ConnectorContractVersions;
import com.orgmemory.core.knowledge.ConnectorCrawlAttemptService;
import com.orgmemory.core.knowledge.ConnectorCrawlAttemptView;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorCrawlCheckpointService;
import com.orgmemory.core.knowledge.ConnectorCrawlOutcome;
import com.orgmemory.core.knowledge.ConnectorIngestionResult;
import com.orgmemory.core.knowledge.ConnectorIngestionService;
import com.orgmemory.core.knowledge.ConnectorPoll;
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
        "orgmemory.authorization.convergence.scheduling-enabled=false",
        "orgmemory.graph-rag.indexing.scheduling-enabled=false",
        "orgmemory.graph-rag.postgres.apache-age-mode=disabled",
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
    private static final String UNREACHABLE_CONNECTION = "T-revoked-workspace";
    private static final String RECORDED_CONNECTION = "T-recorded-workspace";

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
    ConnectorCrawlAttemptService attempts;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void aRestartedDriverResumesInsteadOfReplaying() {
        seedOrganization();
        List<String> firstRun = new ArrayList<>();
        new ConnectorCrawlRunner(
                        producing(batch(STEADY_CONNECTION, "cursor-1"), batch(STEADY_CONNECTION, "cursor-2")),
                        recordingIngestion(firstRun),
                        checkpoints,
                        attempts)
                .runPending();

        assertEquals(List.of("cursor-1", "cursor-2"), firstRun, "a fresh connection ingests both");
        assertEquals(
                "cursor-2",
                checkpoints.lastCompletedCursor(ORG, "slack", STEADY_CONNECTION).orElseThrow(),
                "the checkpoint holds the last cursor the connection completed");

        // A second driver over the same database is what a restart looks like from here.
        List<String> afterRestart = new ArrayList<>();
        new ConnectorCrawlRunner(
                        producing(batch(STEADY_CONNECTION, "cursor-2"), batch(STEADY_CONNECTION, "cursor-3")),
                        recordingIngestion(afterRestart),
                        checkpoints,
                        attempts)
                .runPending();

        assertEquals(List.of("cursor-3"), afterRestart, "the completed cursor is not replayed");
        assertEquals("cursor-3", checkpoints.lastCompletedCursor(ORG, "slack", STEADY_CONNECTION).orElseThrow());
    }

    @Test
    void aRejectedBatchIsCheckpointedPastRatherThanRetriedForever() {
        seedOrganization();
        int[] ingestAttempts = {0};
        ConnectorIngestionService poisoned = failingIngestion(
                ingestAttempts,
                () -> new UnsupportedConnectorPayloadException("Unsupported connector content payload version"));
        ConnectorCrawlRunner driver = new ConnectorCrawlRunner(
                producing(batch(STEADY_CONNECTION, "cursor-poison")), poisoned, checkpoints, attempts);

        driver.runPending();
        assertEquals(1, ingestAttempts[0], "a rejection that will read the same next time is not retried");

        driver.runPending();
        assertEquals(1, ingestAttempts[0], "and the next poll does not offer it again");
    }

    @Test
    void aTransientFailureIsRetriedAndStaysPending() {
        seedOrganization();
        int[] ingestAttempts = {0};
        ConnectorIngestionService flaky =
                failingIngestion(ingestAttempts, () -> new IllegalStateException("database is unavailable"));
        new ConnectorCrawlRunner(
                        producing(batch(FLAKY_CONNECTION, "cursor-flaky")), flaky, checkpoints, attempts)
                .runPending();

        assertEquals(3, ingestAttempts[0], "a transient failure is retried within the run");
        assertTrue(
                checkpoints.lastCompletedCursor(ORG, "slack", FLAKY_CONNECTION).isEmpty(),
                "nothing is checkpointed, so the next poll tries again");
    }

    /**
     * The reading an administrator actually needs. A connection whose credential was revoked
     * produces no batch at all, so nothing is checkpointed and nothing is ingested — and before
     * this was recorded, that was indistinguishable on every screen from a workspace where
     * nothing had been said lately.
     */
    @Test
    void aConnectionThatProducedNoBatchIsRecordedWithItsReason() {
        seedOrganization();
        ConnectorConnectionFailure revoked = new ConnectorConnectionFailure(
                ORG, "slack", UNREACHABLE_CONNECTION, "token_revoked", "Slack refused auth.test: token_revoked");
        new ConnectorCrawlRunner(
                        List.of(() -> new ConnectorPoll(List.of(), List.of(revoked))),
                        recordingIngestion(new ArrayList<>()),
                        checkpoints,
                        attempts)
                .runPending();

        List<ConnectorCrawlAttemptView> recorded = attempts.recent(ORG, "slack", UNREACHABLE_CONNECTION);
        assertEquals(1, recorded.size(), "the connection that produced nothing is still an attempt");
        assertEquals(ConnectorCrawlOutcome.UNAVAILABLE, recorded.getFirst().outcome());
        assertEquals("token_revoked", recorded.getFirst().errorCode(), "Slack's own word for it survives");
        assertTrue(
                checkpoints.lastCompletedCursor(ORG, "slack", UNREACHABLE_CONNECTION).isEmpty(),
                "recording the failure does not mark anything as done");
    }

    /**
     * A batch that reconciles leaves a row saying so, counts included, so a screen can tell a
     * crawl that ran and found nothing from a crawl that never ran.
     */
    @Test
    void aSuccessfulBatchIsRecordedWithWhatItChanged() {
        seedOrganization();
        ConnectorIngestionService ingestion = mock(ConnectorIngestionService.class);
        when(ingestion.ingest(any())).thenReturn(new ConnectorIngestionResult(
                List.of("thread-a", "thread-b"), List.of(), List.of(), List.of("thread-c"), List.of()));
        new ConnectorCrawlRunner(
                        producing(batch(RECORDED_CONNECTION, "cursor-recorded")),
                        ingestion,
                        checkpoints,
                        attempts)
                .runPending();

        ConnectorCrawlAttemptView recorded = attempts.recent(ORG, "slack", RECORDED_CONNECTION).getFirst();
        assertEquals(ConnectorCrawlOutcome.SUCCEEDED, recorded.outcome());
        assertEquals(2, recorded.objectsMaterialized());
        assertEquals(1, recorded.objectsRetired());
        assertTrue(recorded.changedSomething(), "two arrivals and a retirement is not a quiet poll");
    }

    /** A source producing exactly these batches and reporting no unreachable connection. */
    private static List<com.orgmemory.core.knowledge.ConnectorBatchSource> producing(
            ConnectorCrawlBatch... batches) {
        return List.of(() -> ConnectorPoll.of(List.of(batches)));
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
