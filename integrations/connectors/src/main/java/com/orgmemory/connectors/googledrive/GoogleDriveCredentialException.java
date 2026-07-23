package com.orgmemory.connectors.googledrive;

/**
 * Raised when a service account key cannot be used: it is not a key, its private half will not
 * parse, or Google refused to exchange it.
 *
 * <p>Carries Google's own error code where Google supplied one ({@code invalid_grant},
 * {@code unauthorized_client}) and this adapter's own where the failure happened before any
 * request. It never carries the key, and never chains an exception that might: a parse failure's
 * message can contain fragments of what it failed to parse.
 */
public class GoogleDriveCredentialException extends RuntimeException {

    private final String errorCode;

    GoogleDriveCredentialException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /** Google's machine-readable error, or this adapter's, such as {@code invalid_key}. */
    public String errorCode() {
        return errorCode;
    }
}
