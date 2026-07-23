package com.orgmemory.connectors.slack;

/**
 * Resolves the bot token for one Slack connection. Kept as a port because where the token
 * lives is an operational decision that will change — an environment variable is enough for a
 * single workspace, an encrypted per-connection store is not — while the adapter's need for it
 * never does.
 *
 * <p>Implementations must never log, print, or embed the token in an exception message. The
 * value returned here is the only place it should appear.
 */
public interface SlackCredentialProvider {

    /**
     * @param sourceConnectionKey the workspace/connection this crawl is for
     * @return the bot token to authenticate with
     * @throws SlackCredentialUnavailableException when no token is configured for the connection
     */
    String botToken(String sourceConnectionKey);
}
