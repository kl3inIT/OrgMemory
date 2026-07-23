package com.orgmemory.core.shared.secret;

/** Raised when a deployment has no encryption key, so secrets can neither be stored nor read. */
public class SecretsUnavailableException extends RuntimeException {

    public SecretsUnavailableException(String message) {
        super(message);
    }
}
