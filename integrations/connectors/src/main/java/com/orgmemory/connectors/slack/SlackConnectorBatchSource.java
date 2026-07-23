package com.orgmemory.connectors.slack;

import com.orgmemory.core.knowledge.ConnectorAclGrant;
import com.orgmemory.core.knowledge.ConnectorContentItem;
import com.orgmemory.core.knowledge.ConnectorContractVersions;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorPermissionItem;
import com.orgmemory.core.knowledge.SourcePrincipalKind;
import com.orgmemory.core.permission.AccessGate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Crawls a Slack workspace into the crawl-batch contract the governed ledger already ingests.
 * Nothing downstream changes: this only replaces the fixture producer.
 *
 * <p>A thread is the unit, keyed {@code channelId__threadTs} as Onyx keys it. A message on its
 * own is too small to answer anything and a whole channel is too coarse to cite, and the thread
 * root's timestamp is stable across edits, which a rendered-text hash is not — so the id comes
 * from the timestamp and the content revision from the hash. That pairing is what lets an edit
 * re-materialize the same object instead of creating a second one.
 *
 * <p>The care here is in the completeness claim. Declaring a crawl complete authorizes the
 * ledger to retire everything the crawl did not mention, so it is claimed only when this really
 * did enumerate the whole connection: no channel filter, no channel skipped, nothing truncated.
 * Every one of those is an ordinary situation, which is why each explicitly withdraws the claim.
 */
class SlackConnectorBatchSource implements ConnectorBatchSource {

    private static final Logger log = LoggerFactory.getLogger(SlackConnectorBatchSource.class);
    private static final String ALL_CHANNEL_TYPES = "public_channel,private_channel";
    private static final String PUBLIC_CHANNELS_ONLY = "public_channel";
    private static final Set<String> IGNORED_SUBTYPES =
            Set.of("channel_join", "channel_leave", "channel_topic", "channel_purpose", "channel_name");

    private final SlackConnectorProperties properties;
    private final SlackCredentialProvider credentials;
    private final RestClient.Builder restClientBuilder;

    SlackConnectorBatchSource(
            SlackConnectorProperties properties,
            SlackCredentialProvider credentials,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.credentials = credentials;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public List<ConnectorCrawlBatch> pendingBatches() {
        if (!properties.isRunnable()) {
            return List.of();
        }
        SlackWebApiClient client = new SlackWebApiClient(
                restClientBuilder, credentials.botToken(properties.connectionKey()));
        return List.of(crawl(client));
    }

    private ConnectorCrawlBatch crawl(SlackWebApiClient client) {
        Crawl crawl = new Crawl();
        Map<String, SlackUser> usersById = users(client);
        List<JsonNode> channels = channels(client, crawl);

        for (JsonNode channel : channels) {
            try {
                crawlChannel(client, channel, usersById, crawl);
            } catch (SlackApiException failure) {
                // A channel the bot cannot read is not a channel that vanished. Losing it costs
                // this crawl its completeness claim rather than costing the workspace its index.
                log.warn("Slack channel {} was skipped: {}", channel.path("id").asString(""), failure.getMessage());
                crawl.incomplete();
            }
        }

        return new ConnectorCrawlBatch(
                properties.organizationId(),
                "slack",
                properties.connectionKey(),
                properties.knowledgeSpaceId(),
                properties.actorUserId(),
                crawlCursor(crawl),
                ConnectorContractVersions.supported(),
                crawl.identities(),
                crawl.contents,
                crawl.permissions,
                List.of(),
                crawl.complete);
    }

    /**
     * Every human account in the workspace, indexed by id. Bots and deactivated accounts are
     * dropped: neither can be a person to map to, and a bot has no address to map by.
     */
    private Map<String, SlackUser> users(SlackWebApiClient client) {
        Map<String, SlackUser> usersById = new LinkedHashMap<>();
        for (JsonNode member : client.collectPaged("users.list", Map.of(), "members")) {
            if (member.path("deleted").asBoolean(false)
                    || member.path("is_bot").asBoolean(false)
                    || "USLACKBOT".equals(member.path("id").asString(""))) {
                continue;
            }
            JsonNode profile = member.path("profile");
            usersById.put(
                    member.path("id").asString(""),
                    new SlackUser(
                            member.path("id").asString(""),
                            profile.path("email").asString(""),
                            firstNonBlank(
                                    profile.path("display_name").asString(""),
                                    member.path("real_name").asString(""),
                                    member.path("name").asString(""))));
        }
        return usersById;
    }

    /**
     * The channels to crawl. Private channels need a scope the app may not have been granted, so
     * a refusal falls back to public channels rather than failing the crawl — Onyx's behaviour —
     * but the narrower result can no longer speak for the whole connection.
     */
    private List<JsonNode> channels(SlackWebApiClient client, Crawl crawl) {
        List<JsonNode> channels;
        try {
            channels = client.collectPaged(
                    "conversations.list",
                    Map.of("types", ALL_CHANNEL_TYPES, "exclude_archived", "true"),
                    "channels");
        } catch (SlackApiException refused) {
            log.warn("Slack refused private channels ({}); continuing with public channels only",
                    refused.errorCode());
            crawl.incomplete();
            channels = client.collectPaged(
                    "conversations.list",
                    Map.of("types", PUBLIC_CHANNELS_ONLY, "exclude_archived", "true"),
                    "channels");
        }
        if (properties.channels().isEmpty()) {
            return channels;
        }
        // A configured subset cannot speak for what it was never asked to look at.
        crawl.incomplete();
        Set<String> wanted = Set.copyOf(properties.channels());
        return channels.stream()
                .filter(channel -> wanted.contains(channel.path("name").asString("")))
                .toList();
    }

    private void crawlChannel(
            SlackWebApiClient client, JsonNode channel, Map<String, SlackUser> usersById, Crawl crawl) {
        String channelId = channel.path("id").asString("");
        String channelName = channel.path("name").asString(channelId);

        List<String> memberIds = client
                .collectPaged("conversations.members", Map.of("channel", channelId), "members")
                .stream()
                .map(JsonNode::asString)
                .filter(usersById::containsKey)
                .toList();
        memberIds.forEach(memberId -> crawl.observe(usersById.get(memberId)));
        crawl.observeChannel(channelId, channelName, memberIds);

        List<JsonNode> messages = client.collectPaged(
                "conversations.history", Map.of("channel", channelId), "messages");
        int threads = 0;
        for (JsonNode message : messages) {
            if (isIgnorable(message)) {
                continue;
            }
            if (threads++ >= properties.maxThreadsPerChannel()) {
                log.warn("Slack channel {} exceeded {} threads; the crawl no longer covers it fully",
                        channelName, properties.maxThreadsPerChannel());
                crawl.incomplete();
                break;
            }
            crawl.addThread(channelId, channelName, thread(client, channelId, message), usersById);
        }
    }

    /** A thread's messages: the root alone, or the root plus its replies when it has any. */
    private List<JsonNode> thread(SlackWebApiClient client, String channelId, JsonNode root) {
        if (root.path("reply_count").asInt(0) <= 0) {
            return List.of(root);
        }
        return client.collectPaged(
                "conversations.replies",
                Map.of("channel", channelId, "ts", root.path("ts").asString("")),
                "messages");
    }

    /** Joins, leaves, and channel housekeeping say nothing about the work and only add noise. */
    private static boolean isIgnorable(JsonNode message) {
        String subtype = message.path("subtype").asString("");
        return IGNORED_SUBTYPES.contains(subtype)
                || message.path("bot_id").asString("").length() > 0
                || message.path("text").asString("").isBlank();
    }

    /**
     * A cursor that changes when the crawl's result changes. The producer owns its meaning, and
     * what this producer needs from it is that an unchanged workspace does not look like new
     * work: the same crawl twice checkpoints once.
     */
    private static String crawlCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        crawl.contents.forEach(content ->
                material.append(content.externalObjectId()).append('=').append(content.contentRevision()).append(';'));
        material.append("complete=").append(crawl.complete);
        return "slack-" + sha256(material.toString());
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is unavailable", unavailable);
        }
    }

    private record SlackUser(String id, String email, String displayName) {
    }

    /** Accumulates one crawl's payloads and whether it may still claim to have seen everything. */
    private final class Crawl {

        private final Map<String, SlackUser> observedUsers = new LinkedHashMap<>();
        private final Map<String, ChannelIdentity> observedChannels = new LinkedHashMap<>();
        private final List<ConnectorContentItem> contents = new ArrayList<>();
        private final List<ConnectorPermissionItem> permissions = new ArrayList<>();
        private boolean complete = true;

        private void incomplete() {
            complete = false;
        }

        private void observe(SlackUser user) {
            observedUsers.putIfAbsent(user.id(), user);
        }

        private void observeChannel(String channelId, String channelName, List<String> memberIds) {
            observedChannels.put(channelId, new ChannelIdentity(channelId, channelName, memberIds));
        }

        private void addThread(
                String channelId, String channelName, List<JsonNode> messages, Map<String, SlackUser> usersById) {
            if (messages.isEmpty()) {
                return;
            }
            String threadTs = messages.getFirst().path("thread_ts").asString("")
                    .isBlank()
                    ? messages.getFirst().path("ts").asString("")
                    : messages.getFirst().path("thread_ts").asString("");
            String externalObjectId = channelId + "__" + threadTs;
            String body = render(messages, usersById);
            if (body.isBlank()) {
                return;
            }
            contents.add(new ConnectorContentItem(
                    externalObjectId, "#" + channelName, body, sha256(body)));
            permissions.add(new ConnectorPermissionItem(
                    externalObjectId,
                    List.of(new ConnectorAclGrant(
                            SourcePrincipalKind.SOURCE_GROUP, channelId, AccessGate.ALLOW))));
        }

        private String render(List<JsonNode> messages, Map<String, SlackUser> usersById) {
            StringBuilder rendered = new StringBuilder();
            for (JsonNode message : messages) {
                String text = message.path("text").asString("");
                if (text.isBlank()) {
                    continue;
                }
                SlackUser author = usersById.get(message.path("user").asString(""));
                rendered.append(author == null ? "Unknown" : author.displayName())
                        .append(": ")
                        .append(text)
                        .append('\n');
            }
            return rendered.toString().strip();
        }

        /**
         * Users first, then channels: the ledger resolves a group's members against principals it
         * has already seen in this payload.
         *
         * <p>Every observed user is reported as SSO-verified, and that is a claim about Slack
         * rather than about the person. Slack confirms ownership of an address with an emailed
         * code before an account can exist or join a workspace, so an address it reports is one
         * the account holder controls. A source that could not say that would leave this false
         * and wait for an administrator to attest the connection instead.
         */
        private List<ConnectorIdentityItem> identities() {
            List<ConnectorIdentityItem> identities = new ArrayList<>();
            for (SlackUser user : observedUsers.values()) {
                identities.add(new ConnectorIdentityItem(
                        SourcePrincipalKind.SOURCE_USER,
                        user.id(),
                        user.email().isBlank() ? null : user.email(),
                        user.displayName(),
                        true,
                        null,
                        null,
                        List.of()));
            }
            for (ChannelIdentity channel : observedChannels.values()) {
                Set<String> members = new LinkedHashSet<>(channel.memberIds());
                members.retainAll(observedUsers.keySet());
                identities.add(new ConnectorIdentityItem(
                        SourcePrincipalKind.SOURCE_GROUP,
                        channel.id(),
                        null,
                        "#" + channel.name(),
                        false,
                        null,
                        null,
                        List.copyOf(members)));
            }
            return identities;
        }
    }

    private record ChannelIdentity(String id, String name, List<String> memberIds) {
    }
}
