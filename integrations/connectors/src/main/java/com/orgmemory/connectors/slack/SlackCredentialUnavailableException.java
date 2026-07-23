package com.orgmemory.connectors.slack;

/**
 * Raised when no bot token is configured for a connection. The message names the connection
 * and never any part of a token.
 */
public class SlackCredentialUnavailableException extends RuntimeException {

    public SlackCredentialUnavailableException(String message) {
        super(message);
    }
}
