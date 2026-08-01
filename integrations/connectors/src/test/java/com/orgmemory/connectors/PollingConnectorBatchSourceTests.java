package com.orgmemory.connectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.connector.ConnectorComponentState;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.connector.ConnectorContractVersions;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlConfiguration;
import com.orgmemory.core.knowledge.connector.ConnectorPoll;
import com.orgmemory.core.knowledge.connector.ConnectorSyncComponent;
import com.orgmemory.core.shared.secret.SecretValue;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PollingConnectorBatchSourceTests {

    private static final UUID ORG = UUID.fromString("cc000000-0000-4000-8000-000000000001");
    private static final UUID SPACE = UUID.fromString("cc000000-0000-4000-8000-000000000002");
    private static final UUID ACTOR = UUID.fromString("cc000000-0000-4000-8000-000000000003");
    private static final Instant NOW = Instant.parse("2026-08-01T10:00:00Z");

    private ConnectorConnectionDirectory connections;

    @BeforeEach
    void setUp() {
        connections = mock(ConnectorConnectionDirectory.class);
        when(connections.resolveCredential(any(), any(), any()))
                .thenReturn(Optional.of(SecretValue.of("credential-a")));
        when(connections.enabledCrawls("test")).thenReturn(List.of(configuration("{}", null)));
    }

    @Test
    void reusesAClientUntilTheCredentialOrClientConfigurationChanges() {
        TestSource source = new TestSource(connections);

        source.pendingBatches();
        source.pendingBatches();
        assertEquals(1, source.createdClients);

        when(connections.resolveCredential(any(), any(), any()))
                .thenReturn(Optional.of(SecretValue.of("credential-b")));
        source.pendingBatches();
        assertEquals(2, source.createdClients, "credential rotation rebuilds atomically");

        when(connections.enabledCrawls("test"))
                .thenReturn(List.of(configuration("{\"client\":\"changed\"}", null)));
        source.pendingBatches();
        assertEquals(3, source.createdClients, "client-affecting configuration also rebuilds");
    }

    @Test
    void missingCredentialEvictsTheCachedClientBeforeARecovery() {
        TestSource source = new TestSource(connections);
        source.pendingBatches();
        when(connections.resolveCredential(any(), any(), any()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(SecretValue.of("credential-a")));

        ConnectorPoll missing = source.pendingBatches();
        ConnectorPoll recovered = source.pendingBatches();

        assertEquals("no_credential", missing.unavailable().getFirst().errorCode());
        assertEquals(1, recovered.batches().size());
        assertEquals(2, source.createdClients, "the pre-revocation client was not reused");
    }

    @Test
    void disablingThenRecreatingAConnectionRetiresClientAndBothCadenceMaps() {
        Instant requestedAt = NOW.minusSeconds(10);
        when(connections.enabledCrawls("test"))
                .thenReturn(List.of(configuration("{}", requestedAt)))
                .thenReturn(List.of())
                .thenReturn(List.of(configuration("{}", requestedAt)));
        TestSource source = new TestSource(connections);

        source.pendingBatches();
        source.pendingBatches();
        source.pendingBatches();

        assertEquals(List.of(true, true), source.contentDueCalls,
                "a recreated connection cannot inherit a spent crawl-now request");
        assertEquals(2, source.createdClients, "disabled connection client was retired");
    }

    @Test
    void appliesTheMostlyFailedBoundaryOnce() {
        TestSource source = new TestSource(connections);

        source.failures = 1;
        source.total = 3;
        assertEquals(1, source.pendingBatches().batches().size(), "below half is admitted");

        source.failures = 1;
        source.total = 2;
        assertEquals("mostly_failed", source.pendingBatches().unavailable().getFirst().errorCode());

        source.failures = 2;
        source.total = 3;
        assertEquals("mostly_failed", source.pendingBatches().unavailable().getFirst().errorCode());
    }

    @Test
    void unknownRuntimeExceptionsEscapeInsteadOfLookingLikeConnectionActivity() {
        TestSource source = new TestSource(connections);
        source.unknownFailure = true;

        assertThrows(IllegalStateException.class, source::pendingBatches);
    }

    @Test
    void cachedContextDoesNotHaveASecretValueField() {
        Class<?> context = Arrays.stream(PollingConnectorBatchSource.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("ClientContext"))
                .findFirst()
                .orElseThrow();

        assertTrue(Arrays.stream(context.getDeclaredFields())
                .map(Field::getType)
                .noneMatch(SecretValue.class::equals));
    }

    private static ConnectorCrawlConfiguration configuration(
            String sourceConfig, Instant requestedAt) {
        return new ConnectorCrawlConfiguration(
                ORG,
                "test",
                "connection",
                SPACE,
                ACTOR,
                sourceConfig,
                Duration.ofHours(1),
                requestedAt);
    }

    private static final class TestSource extends PollingConnectorBatchSource<Client> {

        private int createdClients;
        private int failures;
        private int total = 1;
        private boolean unknownFailure;
        private final List<Boolean> contentDueCalls = new ArrayList<>();

        private TestSource(ConnectorConnectionDirectory connections) {
            super(connections, Clock.fixed(NOW, ZoneOffset.UTC));
        }

        @Override
        protected String sourceSystem() {
            return "test";
        }

        @Override
        protected String displayName() {
            return "Test";
        }

        @Override
        protected String crawlCursorPrefix() {
            return "test-";
        }

        @Override
        protected Client createClient(
                ConnectorCrawlConfiguration configuration, SecretValue credential) {
            return new Client(++createdClients);
        }

        @Override
        protected String clientConfigurationMaterial(ConnectorCrawlConfiguration configuration) {
            return configuration.sourceConfig();
        }

        @Override
        protected RuntimeException missingCredential(ConnectorCrawlConfiguration configuration) {
            return new ExpectedFailure("credential missing");
        }

        @Override
        protected boolean isExpectedConnectorFailure(RuntimeException failure) {
            return failure instanceof ExpectedFailure;
        }

        @Override
        protected String errorCodeOf(RuntimeException failure) {
            return "no_credential";
        }

        @Override
        protected CrawlResult crawl(
                Client client,
                ConnectorCrawlConfiguration configuration,
                boolean contentDue) {
            if (unknownFailure) {
                throw new IllegalStateException("programmer defect");
            }
            contentDueCalls.add(contentDue);
            List<ConnectorComponentState> states = new ArrayList<>();
            if (contentDue) {
                states.add(ConnectorComponentState.complete(
                        ConnectorSyncComponent.CONTENT, "content"));
            }
            states.add(ConnectorComponentState.complete(
                    ConnectorSyncComponent.PERMISSION, "permission"));
            states.add(ConnectorComponentState.complete(
                    ConnectorSyncComponent.MEMBERSHIP, "membership"));
            ConnectorCrawlBatch batch = new ConnectorCrawlBatch(
                    configuration.organizationId(),
                    sourceSystem(),
                    configuration.sourceConnectionKey(),
                    configuration.knowledgeSpaceId(),
                    configuration.actorUserId(),
                    crawlCursor(states),
                    ConnectorContractVersions.supported(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    states,
                    false);
            return result(batch, failures, total, "units");
        }
    }

    private record Client(int number) {
    }

    private static final class ExpectedFailure extends RuntimeException {

        private ExpectedFailure(String message) {
            super(message);
        }
    }
}
