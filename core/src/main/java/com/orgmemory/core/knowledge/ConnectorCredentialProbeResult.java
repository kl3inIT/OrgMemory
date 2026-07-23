package com.orgmemory.core.knowledge;

/**
 * What one credential turned out to be, in vocabulary no single source owns.
 *
 * <p>Everything here is safe to show an administrator and to put in a log: what the credential
 * authenticated against, who it authenticated as, and the source's own error code when it
 * refused. The credential itself appears nowhere.
 *
 * <p>{@code authenticated} and {@code canReadContent} are separate answers, and the separation
 * is the point of probing at all. A credential can be live and still useless, because
 * authenticating says nothing about what was granted with it — a Slack app installed without
 * {@code channels:read}, or a Drive service account nobody shared a folder with, both
 * authenticate perfectly and then fail at the first crawl, hours later, as an indexing failure
 * nobody connects to the day it was configured.
 *
 * @param connectionKey what the connection will be keyed on — the workspace id, the domain — so
 *                      an administrator is told it rather than asked for it
 */
public record ConnectorCredentialProbeResult(
        boolean authenticated,
        String connectionKey,
        String accountName,
        String identityName,
        boolean canReadContent,
        String errorCode) {

    /** The source would not authenticate at all, so there is no account to report. */
    public static ConnectorCredentialProbeResult rejected(String errorCode) {
        return new ConnectorCredentialProbeResult(false, null, null, null, false, errorCode);
    }

    /** Authenticated, but what a crawl needs is not there. */
    public static ConnectorCredentialProbeResult withoutContentAccess(
            String connectionKey, String accountName, String identityName, String errorCode) {
        return new ConnectorCredentialProbeResult(
                true, connectionKey, accountName, identityName, false, errorCode);
    }

    public static ConnectorCredentialProbeResult usable(
            String connectionKey, String accountName, String identityName) {
        return new ConnectorCredentialProbeResult(true, connectionKey, accountName, identityName, true, null);
    }

    /** Nothing is stored for this connection, so there is nothing to check. */
    public static ConnectorCredentialProbeResult noCredential() {
        return new ConnectorCredentialProbeResult(false, null, null, null, false, "no_credential");
    }
}
