package com.orgmemory.core.knowledge.connector;



import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remembers the last observed and last successfully reconciled cursor for every component of
 * each source connection, so a driver restart does not replay unrelated work.
 *
 * <p>A checkpoint is deliberately not a correctness guarantee. Re-ingesting a batch is safe —
 * content materializes once per revision and ACL generations reconcile deterministically — so
 * this exists to avoid pointless work, not to prevent harm. It retains one row per component,
 * not a growing history of dead cursors.
 */
@Service
public class ConnectorCrawlCheckpointService {

    private final ConnectorCrawlCheckpointRepository checkpoints;

    ConnectorCrawlCheckpointService(ConnectorCrawlCheckpointRepository checkpoints) {
        this.checkpoints = checkpoints;
    }

    /** Components whose exact source observation has not yet been handled. */
    @Transactional(readOnly = true)
    public Set<ConnectorSyncComponent> pendingComponents(ConnectorCrawlBatch batch) {
        Objects.requireNonNull(batch, "batch");
        EnumSet<ConnectorSyncComponent> pending = EnumSet.noneOf(ConnectorSyncComponent.class);
        for (ConnectorComponentState state : batch.componentStates()) {
            boolean observed = find(batch, state.component())
                    .map(checkpoint -> checkpoint.getObservedCursor().equals(state.cursor()))
                    .orElse(false);
            if (!observed) {
                pending.add(state.component());
            }
        }
        return Set.copyOf(pending);
    }

    /** The last successfully reconciled cursor for one component. */
    @Transactional(readOnly = true)
    public Optional<String> lastCompletedCursor(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            ConnectorSyncComponent component) {
        return checkpoints
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndComponent(
                        organizationId,
                        sourceSystem.trim(),
                        sourceConnectionKey.trim(),
                        component)
                .map(ConnectorCrawlCheckpoint::toView)
                .map(ConnectorComponentCheckpointView::lastSuccessfulCursor);
    }

    /** Component health and progress for an administrator. */
    @Transactional(readOnly = true)
    public List<ConnectorComponentCheckpointView> describe(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        return checkpoints
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyOrderByComponent(
                        organizationId, sourceSystem.trim(), sourceConnectionKey.trim())
                .stream()
                .map(ConnectorCrawlCheckpoint::toView)
                .toList();
    }

    /**
     * Marks only the components that ingestion handled. Complete source evidence advances
     * last-successful state; incomplete evidence advances observation only.
     */
    @Transactional
    public void complete(
            ConnectorCrawlBatch batch, Set<ConnectorSyncComponent> completedComponents) {
        observe(batch, completedComponents, true);
    }

    /** Marks invalid bytes observed without claiming they reconciled successfully. */
    @Transactional
    public void reject(ConnectorCrawlBatch batch, Set<ConnectorSyncComponent> components) {
        observe(batch, components, false);
    }

    private void observe(
            ConnectorCrawlBatch batch,
            Set<ConnectorSyncComponent> components,
            boolean reconciled) {
        Objects.requireNonNull(batch, "batch");
        Objects.requireNonNull(components, "components");
        Instant now = Instant.now();
        for (ConnectorSyncComponent component : components) {
            ConnectorComponentState state = batch.componentState(component);
            ConnectorCrawlCheckpoint checkpoint = find(batch, component).orElse(null);
            if (checkpoint == null) {
                checkpoints.save(new ConnectorCrawlCheckpoint(
                        batch.organizationId(),
                        batch.sourceSystem(),
                        batch.sourceConnectionKey(),
                        state,
                        now,
                        reconciled));
                continue;
            }
            checkpoint.advanceTo(state, now, reconciled);
            checkpoints.save(checkpoint);
        }
    }

    private Optional<ConnectorCrawlCheckpoint> find(
            ConnectorCrawlBatch batch, ConnectorSyncComponent component) {
        return checkpoints
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndComponent(
                        batch.organizationId(),
                        batch.sourceSystem(),
                        batch.sourceConnectionKey(),
                        component);
    }
}
