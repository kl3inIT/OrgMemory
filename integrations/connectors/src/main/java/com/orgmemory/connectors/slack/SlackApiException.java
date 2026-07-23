package com.orgmemory.connectors.slack;

/**
 * Raised when Slack refuses a call or answers with a shape this adapter cannot read. Carries
 * the method and Slack's own error code, never the token that authenticated the call.
 */
public class SlackApiException extends RuntimeException {

    private final String errorCode;

    public SlackApiException(String message) {
        this(message, null);
    }

    public SlackApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /** Slack's machine-readable error, such as {@code missing_scope} or {@code not_in_channel}. */
    public String errorCode() {
        return errorCode;
    }
}
