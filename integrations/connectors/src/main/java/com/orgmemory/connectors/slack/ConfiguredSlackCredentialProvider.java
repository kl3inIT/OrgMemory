package com.orgmemory.connectors.slack;

/**
 * Resolves the bot token from configuration, which in practice means the environment. Enough
 * for the single workspace this increment crawls, and deliberately not more: an encrypted
 * per-connection store is the right answer for many workspaces, and it replaces this class
 * without anything else moving.
 *
 * <p>The token is only ever returned, never logged or put in a message. A connection asking for
 * a token it has no configuration for is told which connection failed and nothing else.
 */
class ConfiguredSlackCredentialProvider implements SlackCredentialProvider {

    private final SlackConnectorProperties properties;

    ConfiguredSlackCredentialProvider(SlackConnectorProperties properties) {
        this.properties = properties;
    }

    @Override
    public String botToken(String sourceConnectionKey) {
        if (!properties.connectionKey().equals(sourceConnectionKey)) {
            throw new SlackCredentialUnavailableException(
                    "No Slack credential is configured for connection " + sourceConnectionKey);
        }
        if (properties.botToken().isBlank()) {
            throw new SlackCredentialUnavailableException(
                    "The Slack bot token is not configured for connection " + sourceConnectionKey);
        }
        return properties.botToken();
    }
}
