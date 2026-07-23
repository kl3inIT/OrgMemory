package com.orgmemory.connectors.slack;

/**
 * What one bot token turned out to be. Everything here is safe to show an administrator and to
 * put in a log: the workspace it authenticated against, the identity it authenticated as, and
 * Slack's own error code when it refused. The token itself appears nowhere.
 *
 * <p>{@code authenticated} and {@code canListChannels} are separate answers on purpose. A token
 * can pass {@code auth.test} and still be useless, because authentication says the token is live
 * and says nothing about which scopes were granted with it — a workspace that installed the app
 * without {@code channels:read} would look healthy here and then fail at the first crawl.
 */
public record SlackCredentialProbeResult(
        boolean authenticated,
        String workspaceName,
        String workspaceId,
        String botName,
        boolean canListChannels,
        String errorCode) {

    /** Slack would not authenticate at all, so there is no workspace to report. */
    static SlackCredentialProbeResult rejected(String errorCode) {
        return new SlackCredentialProbeResult(false, null, null, null, false, errorCode);
    }

    /** Authenticated, but the crawl scopes are not there. */
    static SlackCredentialProbeResult withoutChannelAccess(
            String workspaceName, String workspaceId, String botName, String errorCode) {
        return new SlackCredentialProbeResult(true, workspaceName, workspaceId, botName, false, errorCode);
    }

    static SlackCredentialProbeResult usable(String workspaceName, String workspaceId, String botName) {
        return new SlackCredentialProbeResult(true, workspaceName, workspaceId, botName, true, null);
    }
}
