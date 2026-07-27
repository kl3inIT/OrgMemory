package com.orgmemory.connectors.github;

/** A GitHub REST refusal with a stable, non-secret reason suitable for connection activity. */
class GitHubApiException extends RuntimeException {

    private final String errorCode;

    GitHubApiException(String message) {
        this(message, null);
    }

    GitHubApiException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    String errorCode() {
        return errorCode;
    }
}

