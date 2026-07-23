package com.orgmemory.core.knowledge;

/**
 * One object that failed to reconcile, isolated so the rest of the batch proceeds. The
 * reason is a short diagnostic, not sensitive content.
 */
public record ConnectorItemFailure(String externalObjectId, String reason) {
}
