package com.orgmemory.core.knowledge;

import java.util.List;

/**
 * The outcome of ingesting one crawl batch, per object. {@code materialized} first
 * materialized content, {@code rotated} converged an ACL generation without re-materializing
 * content, {@code contentDeferred} rotated the ACL but saw a changed content revision whose
 * re-materialization is deferred to the live increment, {@code retired} tombstoned an object,
 * and {@code failures} isolate objects that could not be reconciled.
 */
public record ConnectorIngestionResult(
        List<String> materialized,
        List<String> rotated,
        List<String> contentDeferred,
        List<String> retired,
        List<ConnectorItemFailure> failures) {

    public ConnectorIngestionResult {
        materialized = List.copyOf(materialized);
        rotated = List.copyOf(rotated);
        contentDeferred = List.copyOf(contentDeferred);
        retired = List.copyOf(retired);
        failures = List.copyOf(failures);
    }
}
