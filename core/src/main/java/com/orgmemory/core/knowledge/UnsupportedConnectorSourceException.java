package com.orgmemory.core.knowledge;

/**
 * Raised when a crawl names a source system no adapter contributed a profile for. Permanent
 * rather than transient: retrying cannot install a connector, so a batch that hits this is
 * checkpointed past instead of being attempted again.
 */
public class UnsupportedConnectorSourceException extends RuntimeException {

    public UnsupportedConnectorSourceException(String message) {
        super(message);
    }
}
