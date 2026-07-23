package com.orgmemory.core.knowledge;

import java.util.List;

/**
 * The outcome of ingesting one crawl batch, per object. {@code materialized} first
 * materialized content, {@code rotated} converged an ACL generation without re-materializing
 * content, {@code retired} tombstoned an object, and {@code failures} isolate objects that
 * could not be reconciled.
 */
public record ConnectorIngestionResult(
        List<String> materialized,
        List<String> rotated,
        List<String> retired,
        List<ConnectorItemFailure> failures) {

    public ConnectorIngestionResult {
        materialized = List.copyOf(materialized);
        rotated = List.copyOf(rotated);
        retired = List.copyOf(retired);
        failures = List.copyOf(failures);
    }
}
