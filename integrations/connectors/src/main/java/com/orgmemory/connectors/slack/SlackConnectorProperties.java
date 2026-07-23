package com.orgmemory.connectors.slack;

import java.util.List;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What one Slack connection needs to crawl into the governed ledger. The Slack half is the
 * workspace and its token; the OrgMemory half — tenant, Knowledge Space, and the user recorded
 * as author and audit actor — has no Slack equivalent and has to be configured.
 *
 * <p>Everything is unset by default so the adapter contributes nothing until a deployment opts
 * in, matching the connector driver it feeds.
 *
 * @param enabled            whether this adapter should produce batches at all
 * @param botToken           the {@code xoxb-} bot token; supply through the environment as
 *                           {@code ORGMEMORY_CONNECTOR_SLACK_BOT_TOKEN} and never in a committed
 *                           file
 * @param connectionKey      the workspace/team id this connection is keyed by
 * @param organizationId     the tenant crawled content belongs to
 * @param knowledgeSpaceId   the Knowledge Space crawled content is published into
 * @param actorUserId        the connection owner recorded as author, publication owner, and actor
 * @param channels           channel names to crawl; empty means every channel the bot can see
 * @param maxThreadsPerChannel a bound on one crawl so a large workspace cannot run unbounded
 */
@ConfigurationProperties("orgmemory.connector.slack")
public record SlackConnectorProperties(
        Boolean enabled,
        String botToken,
        String connectionKey,
        UUID organizationId,
        UUID knowledgeSpaceId,
        UUID actorUserId,
        List<String> channels,
        Integer maxThreadsPerChannel) {

    public SlackConnectorProperties {
        enabled = enabled != null && enabled;
        botToken = botToken == null ? "" : botToken.strip();
        connectionKey = connectionKey == null ? "" : connectionKey.strip();
        channels = channels == null ? List.of() : List.copyOf(channels);
        maxThreadsPerChannel = maxThreadsPerChannel == null || maxThreadsPerChannel <= 0
                ? 500
                : maxThreadsPerChannel;
    }

    /** Whether enough is configured to crawl. A partially configured connection stays inert. */
    public boolean isRunnable() {
        return enabled
                && !botToken.isBlank()
                && !connectionKey.isBlank()
                && organizationId != null
                && knowledgeSpaceId != null
                && actorUserId != null;
    }

    /**
     * Deliberately omits the token. This record ends up in logs, actuator output, and failure
     * messages, and the default record {@code toString} would carry the secret into all three.
     */
    @Override
    public String toString() {
        return "SlackConnectorProperties[enabled=" + enabled
                + ", botToken=" + (botToken.isBlank() ? "<unset>" : "<redacted>")
                + ", connectionKey=" + connectionKey
                + ", organizationId=" + organizationId
                + ", knowledgeSpaceId=" + knowledgeSpaceId
                + ", actorUserId=" + actorUserId
                + ", channels=" + channels
                + ", maxThreadsPerChannel=" + maxThreadsPerChannel + "]";
    }
}
