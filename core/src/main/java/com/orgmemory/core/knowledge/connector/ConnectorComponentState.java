package com.orgmemory.core.knowledge.connector;


/**
 * One component's source-owned cursor and capture completeness in a crawl batch.
 * Incomplete source evidence is an observation, not a technical failure or a successful
 * authorization synchronization.
 */
public record ConnectorComponentState(
        ConnectorSyncComponent component,
        String cursor,
        ConnectorCaptureStatus captureStatus,
        String incompleteReason) {

    public ConnectorComponentState {
        if (component == null) {
            throw new IllegalArgumentException("connector component is required");
        }
        if (cursor == null || cursor.isBlank()) {
            throw new IllegalArgumentException("connector component cursor is required");
        }
        cursor = cursor.trim();
        if (captureStatus == null) {
            throw new IllegalArgumentException("connector component captureStatus is required");
        }
        incompleteReason = normalize(incompleteReason);
        if (captureStatus == ConnectorCaptureStatus.COMPLETE && incompleteReason != null) {
            throw new IllegalArgumentException(
                    "complete connector component cannot have an incompleteReason");
        }
        if (captureStatus == ConnectorCaptureStatus.INCOMPLETE && incompleteReason == null) {
            throw new IllegalArgumentException(
                    "incomplete connector component requires an incompleteReason");
        }
    }

    public static ConnectorComponentState complete(
            ConnectorSyncComponent component,
            String cursor) {
        return new ConnectorComponentState(
                component,
                cursor,
                ConnectorCaptureStatus.COMPLETE,
                null);
    }

    public static ConnectorComponentState incomplete(
            ConnectorSyncComponent component,
            String cursor,
            String reason) {
        return new ConnectorComponentState(
                component,
                cursor,
                ConnectorCaptureStatus.INCOMPLETE,
                reason);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
