package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.connector.ConnectorCaptureStatus;
import com.orgmemory.core.knowledge.connector.ConnectorSyncComponent;

import java.time.Instant;

/** Operational state of one independently checkpointed connector component. */
public record ConnectorComponentCheckpointView(
        ConnectorSyncComponent component,
        String observedCursor,
        ConnectorCaptureStatus captureStatus,
        String incompleteReason,
        Instant observedAt,
        String lastSuccessfulCursor,
        Instant lastSuccessfulAt) {
}
