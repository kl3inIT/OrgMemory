package com.orgmemory.connectors;

import com.orgmemory.core.knowledge.connector.ConnectorBatchSource;
import com.orgmemory.core.knowledge.connector.ConnectorComponentState;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionFailure;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlConfiguration;
import com.orgmemory.core.knowledge.connector.ConnectorPoll;
import com.orgmemory.core.shared.Digests;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Final polling lifecycle shared by live connector adapters.
 *
 * <p>Adapters still own source calls, mappings, completeness evidence, component cursors, and
 * what constitutes one failed source unit. This template owns the reliability mechanics that
 * must not be optional: connection isolation, client lifecycle, content cadence, admission,
 * failure activity, and the outer cursor envelope.
 */
public abstract class PollingConnectorBatchSource<C> implements ConnectorBatchSource {

    private static final double MOSTLY_FAILED_ABORT_SHARE = 0.5;
    private static final String MOSTLY_FAILED_ERROR_CODE = "mostly_failed";

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ConnectorConnectionDirectory connections;
    private final Clock clock;
    private final ContentCadence contentCadence = new ContentCadence();
    private final Map<ConnectorPollingIdentity, ClientContext<C>> clients =
            new ConcurrentHashMap<>();

    protected PollingConnectorBatchSource(
            ConnectorConnectionDirectory connections, Clock clock) {
        this.connections = connections;
        this.clock = clock;
    }

    @Override
    public final ConnectorPoll pendingBatches() {
        List<ConnectorCrawlConfiguration> configurations =
                List.copyOf(connections.enabledCrawls(sourceSystem()));
        Set<ConnectorPollingIdentity> active = new HashSet<>();
        configurations.forEach(configuration ->
                active.add(ConnectorPollingIdentity.of(configuration, sourceSystem())));

        List<ConnectorCrawlBatch> batches = new ArrayList<>();
        List<ConnectorConnectionFailure> unavailable = new ArrayList<>();
        try {
            for (ConnectorCrawlConfiguration configuration : configurations) {
                try {
                    batches.add(pollConnection(configuration));
                } catch (RuntimeException failure) {
                    if (!isExpectedConnectorFailure(failure)
                            && !(failure instanceof MostlyFailedConnectorException)) {
                        throw failure;
                    }
                    log.warn(
                            "{} connection {} produced no batch this poll: {}",
                            displayName(),
                            configuration.sourceConnectionKey(),
                            failure.getMessage());
                    unavailable.add(new ConnectorConnectionFailure(
                            configuration.organizationId(),
                            sourceSystem(),
                            configuration.sourceConnectionKey(),
                            failure instanceof MostlyFailedConnectorException
                                    ? MOSTLY_FAILED_ERROR_CODE
                                    : errorCodeOf(failure),
                            failure.getMessage()));
                }
            }
        } finally {
            clients.keySet().removeIf(identity -> !active.contains(identity));
            contentCadence.retainOnly(active);
        }
        return new ConnectorPoll(batches, unavailable);
    }

    /** Polls one configuration; package adapters expose this through their existing test seam. */
    protected final ConnectorCrawlBatch pollConnection(
            ConnectorCrawlConfiguration configuration) {
        C client = clientFor(configuration);
        Instant now = clock.instant();
        boolean contentDue = contentCadence.contentDue(configuration, now);
        CrawlResult result = crawl(client, configuration, contentDue);
        rejectIfMostlyFailed(result);
        if (contentDue) {
            contentCadence.contentCrawled(configuration, now);
        }
        return result.batch();
    }

    /** Exact historical outer-cursor algorithm; the literal prefix is adapter-owned. */
    protected final String crawlCursor(List<ConnectorComponentState> states) {
        String material = states.stream()
                .map(state -> state.component() + "=" + state.cursor())
                .sorted()
                .reduce((left, right) -> left + ";" + right)
                .orElse("");
        return crawlCursorPrefix() + Digests.sha256(material);
    }

    /** Creates a validated result with adapter-owned unit counts for this pass. */
    protected final CrawlResult result(
            ConnectorCrawlBatch batch, int failedUnits, int totalUnits, String unitName) {
        return new CrawlResult(batch, failedUnits, totalUnits, unitName);
    }

    private C clientFor(ConnectorCrawlConfiguration configuration) {
        ConnectorPollingIdentity identity =
                ConnectorPollingIdentity.of(configuration, sourceSystem());
        SecretValue credential = connections
                .resolveCredential(
                        configuration.organizationId(),
                        sourceSystem(),
                        configuration.sourceConnectionKey())
                .orElseGet(() -> {
                    clients.remove(identity);
                    throw missingCredential(configuration);
                });
        String credentialRevision = Digests.sha256(credential.expose());
        String configurationMaterial = clientConfigurationMaterial(configuration);
        String configurationRevision =
                Digests.sha256(configurationMaterial == null ? "" : configurationMaterial);
        try {
            return clients.compute(identity, (ignored, current) -> {
                if (current != null
                        && current.credentialRevision().equals(credentialRevision)
                        && current.configurationRevision().equals(configurationRevision)) {
                    return current;
                }
                return new ClientContext<>(
                        credentialRevision,
                        configurationRevision,
                        createClient(configuration, credential));
            }).client();
        } catch (RuntimeException failure) {
            // ConcurrentHashMap retains the old value when a remapping function throws. A
            // rejected rotation must not leave the superseded credential-derived client alive.
            clients.remove(identity);
            throw failure;
        }
    }

    private static void rejectIfMostlyFailed(CrawlResult result) {
        if (result.failedUnits() > 0
                && result.failedUnits() >= result.totalUnits() * MOSTLY_FAILED_ABORT_SHARE) {
            throw new MostlyFailedConnectorException(
                    "Connector crawl abandoned: " + result.failedUnits() + " of "
                            + result.totalUnits() + " " + result.unitName()
                            + " could not be read");
        }
    }

    protected abstract String sourceSystem();

    protected abstract String displayName();

    /** Includes the trailing separator, for example {@code google-drive-}. */
    protected abstract String crawlCursorPrefix();

    protected abstract C createClient(
            ConnectorCrawlConfiguration configuration, SecretValue credential);

    /** Non-secret settings that change client identity; empty for credential-only clients. */
    protected String clientConfigurationMaterial(ConnectorCrawlConfiguration configuration) {
        return "";
    }

    protected abstract RuntimeException missingCredential(
            ConnectorCrawlConfiguration configuration);

    protected abstract boolean isExpectedConnectorFailure(RuntimeException failure);

    protected abstract String errorCodeOf(RuntimeException failure);

    protected abstract CrawlResult crawl(
            C client, ConnectorCrawlConfiguration configuration, boolean contentDue);

    /** One adapter attempt and its pass-specific admission denominator. */
    protected record CrawlResult(
            ConnectorCrawlBatch batch, int failedUnits, int totalUnits, String unitName) {

        public CrawlResult {
            if (batch == null) {
                throw new IllegalArgumentException("batch is required");
            }
            if (failedUnits < 0 || totalUnits < 0 || failedUnits > totalUnits) {
                throw new IllegalArgumentException("invalid failed/total unit counts");
            }
            if (unitName == null || unitName.isBlank()) {
                throw new IllegalArgumentException("unitName is required");
            }
        }
    }

    private record ClientContext<C>(
            String credentialRevision, String configurationRevision, C client) {
    }

    private static final class MostlyFailedConnectorException extends RuntimeException {

        private MostlyFailedConnectorException(String message) {
            super(message);
        }
    }
}
