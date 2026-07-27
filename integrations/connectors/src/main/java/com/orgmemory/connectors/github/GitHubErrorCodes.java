package com.orgmemory.connectors.github;

/** One mapping from internal GitHub failures to stable operator-facing activity codes. */
final class GitHubErrorCodes {

    private GitHubErrorCodes() {
    }

    static String of(RuntimeException failure) {
        if (failure instanceof GitHubCredentialException credential) {
            return credential.errorCode();
        }
        if (failure instanceof GitHubApiException api && api.errorCode() != null) {
            return api.errorCode();
        }
        return "github_error";
    }
}
