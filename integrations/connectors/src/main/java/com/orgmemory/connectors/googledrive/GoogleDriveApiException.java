package com.orgmemory.connectors.googledrive;

/**
 * Raised when Drive refuses a call or answers with a shape this adapter cannot read. Carries the
 * operation and Google's own reason, never the credential that authenticated the call.
 */
public class GoogleDriveApiException extends RuntimeException {

    private final String errorCode;

    GoogleDriveApiException(String message) {
        this(message, null);
    }

    GoogleDriveApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    /** Google's machine-readable reason, such as {@code insufficientPermissions} or {@code notFound}. */
    public String errorCode() {
        return errorCode;
    }
}
