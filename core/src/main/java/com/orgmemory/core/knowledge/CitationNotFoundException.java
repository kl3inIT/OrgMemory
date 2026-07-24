package com.orgmemory.core.knowledge;

/**
 * Opaque citation lookup failure. Missing and unauthorized citations share the
 * same result so callers cannot use the endpoint as an existence oracle.
 */
public final class CitationNotFoundException extends RuntimeException {

    public CitationNotFoundException() {
        super("The requested citation is not available");
    }
}
