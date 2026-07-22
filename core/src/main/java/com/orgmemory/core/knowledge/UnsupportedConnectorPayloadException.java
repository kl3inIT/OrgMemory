package com.orgmemory.core.knowledge;

/**
 * Raised when a crawl batch declares a staging-contract payload version this build does
 * not understand. Fail closed: an unrecognized shape is never guessed at or partially
 * applied.
 */
public class UnsupportedConnectorPayloadException extends RuntimeException {

    public UnsupportedConnectorPayloadException(String message) {
        super(message);
    }
}
