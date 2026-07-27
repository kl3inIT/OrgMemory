package com.orgmemory.connectors.github;

/** A GitHub App credential that cannot be parsed, signed, or exchanged. */
class GitHubCredentialException extends RuntimeException {

    private final String errorCode;

    GitHubCredentialException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    String errorCode() {
        return errorCode;
    }
}

