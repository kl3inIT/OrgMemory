package com.orgmemory.connectors.slack;

import com.orgmemory.core.knowledge.ConnectorScope;
import com.orgmemory.core.knowledge.ConnectorScopeBrowser;
import com.orgmemory.core.shared.secret.SecretValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * The channels a connection could be pointed at, and what choosing each one would take.
 *
 * <p>Three answers, and the difference between them is the whole point of listing rather than
 * asking an administrator to type channel names. A channel the bot is in is ready. A public
 * channel it is not in can be joined, so choosing it is enough — Slack lets any member of a
 * workspace join a public channel, and the bot is one. A private channel cannot: Slack will not
 * let an app add itself to one, so somebody inside has to invite it, and saying so here is more
 * use than a crawl that quietly returns nothing.
 */
public class SlackScopeBrowser implements ConnectorScopeBrowser {

    private static final String ALL_CHANNEL_TYPES = "public_channel,private_channel";
    private static final String PUBLIC_CHANNELS_ONLY = "public_channel";

    private final RestClient.Builder restClientBuilder;

    public SlackScopeBrowser(RestClient.Builder restClientBuilder) {
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public String sourceSystem() {
        return SlackSourceProfile.SOURCE_SYSTEM;
    }

    @Override
    public List<ConnectorScope> scopes(SecretValue credential, String sourceConfig) {
        Objects.requireNonNull(credential, "credential");
        SlackWebApiClient client = new SlackWebApiClient(restClientBuilder, credential.expose());
        // The bot's own handle, so the instruction for a private channel is the command to run
        // rather than a description of it.
        String botHandle = client.call("auth.test", Map.of()).path("user").asString("");

        List<JsonNode> channels;
        try {
            channels = client.collectPaged(
                    "conversations.list",
                    Map.of("types", ALL_CHANNEL_TYPES, "exclude_archived", "true"),
                    "channels");
        } catch (SlackApiException refusedPrivate) {
            // The app was installed without the private-channel scopes. Public channels are still
            // worth listing; the ones it cannot see simply are not offered.
            channels = client.collectPaged(
                    "conversations.list",
                    Map.of("types", PUBLIC_CHANNELS_ONLY, "exclude_archived", "true"),
                    "channels");
        }

        List<ConnectorScope> scopes = new ArrayList<>();
        for (JsonNode channel : channels) {
            String id = channel.path("id").asString("");
            if (id.isEmpty()) {
                continue;
            }
            String name = "#" + channel.path("name").asString(id);
            if (channel.path("is_member").asBoolean(true)) {
                scopes.add(ConnectorScope.reachable(id, name));
            } else if (channel.path("is_private").asBoolean(false)) {
                scopes.add(ConnectorScope.barred(id, name, inviteCommand(botHandle)));
            } else {
                scopes.add(ConnectorScope.admissible(id, name));
            }
        }
        return List.copyOf(scopes);
    }

    private static String inviteCommand(String botHandle) {
        return botHandle.isBlank()
                ? "Slack will not let an app add itself to a private channel; invite the bot there."
                : "Run /invite @" + botHandle + " in that channel.";
    }
}
