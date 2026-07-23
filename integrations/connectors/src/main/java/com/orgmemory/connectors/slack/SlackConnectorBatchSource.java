package com.orgmemory.connectors.slack;

import com.orgmemory.core.knowledge.ConnectorAclGrant;
import com.orgmemory.core.knowledge.ConnectorContentItem;
import com.orgmemory.core.knowledge.ConnectorContractVersions;
import com.orgmemory.core.knowledge.ConnectorObjectDirectory;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorPermissionItem;
import com.orgmemory.core.knowledge.SourcePrincipalKind;
import com.orgmemory.core.permission.AccessGate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
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
    /** Housekeeping events Slack records as messages. They say nothing about the work. */
    private static final Set<String> IGNORED_SUBTYPES = Set.of(
            "channel_join", "channel_leave", "channel_topic", "channel_purpose", "channel_name",
            "channel_archive", "channel_unarchive", "channel_posting_permissions",
            "group_join", "group_leave", "group_archive", "group_unarchive",
            "pinned_item", "unpinned_item", "ekm_access_denied");

    /** Above this share of unreadable channels the run is a failure, not a crawl. */
    private static final double FAILED_CHANNEL_ABORT_SHARE = 0.5;

    private final SlackConnectorProperties properties;
    private final SlackCredentialProvider credentials;
    private final ConnectorObjectDirectory objects;
    private final RestClient.Builder restClientBuilder;
    private final Clock clock;
    private final AtomicReference<Instant> contentCrawlDueAt = new AtomicReference<>(Instant.EPOCH);

    SlackConnectorBatchSource(
            SlackConnectorProperties properties,
            SlackCredentialProvider credentials,
            ConnectorObjectDirectory objects,
            RestClient.Builder restClientBuilder) {
        this(properties, credentials, objects, restClientBuilder, Clock.systemUTC());
    }

    SlackConnectorBatchSource(
            SlackConnectorProperties properties,
            SlackCredentialProvider credentials,
            ConnectorObjectDirectory objects,
            RestClient.Builder restClientBuilder,
            Clock clock) {
        this.properties = properties;
        this.credentials = credentials;
        this.objects = objects;
        this.restClientBuilder = restClientBuilder;
        this.clock = clock;
    }

    /**
     * One batch per poll, and which kind depends on what is due.
     *
     * <p>Access changes daily and content rarely, so re-reading every message body to answer "who
     * can see this now" is the expensive mistake. Between content crawls this produces a
     * permissions crawl instead: channels and their members, applied to the objects the ledger
     * already holds. That costs a call per channel rather than a call per thread.
     *
     * <p>The due time is held in memory on purpose. Losing it costs one extra content crawl after
     * a restart, and the alternative — another durable row to keep honest — buys nothing against
     * a failure that benign.
     */
    @Override
    public List<ConnectorCrawlBatch> pendingBatches() {
        if (!properties.isRunnable()) {
            return List.of();
        }
        SlackWebApiClient client = new SlackWebApiClient(
                restClientBuilder, credentials.botToken(properties.connectionKey()));
        Instant now = clock.instant();
        if (now.isBefore(contentCrawlDueAt.get())) {
            return List.of(permissionsCrawl(client));
        }
        contentCrawlDueAt.set(now.plus(properties.contentCrawlInterval()));
        return List.of(crawl(client));
    }

    /**
     * Who may currently read what, without reading anything. Channels and their members come from
     * Slack; the objects those grants apply to come from the ledger, because asking Slack to
     * enumerate them again would mean paging every channel's history for ids we already have.
     *
     * <p>This batch never claims completeness, and the reason is worth stating plainly: its object
     * list is our own record rather than the source's. A crawl that claimed to have enumerated the
     * connection on that basis would be confirming itself, and the ledger would then be entitled
     * to retire anything the circular answer left out.
     */
    private ConnectorCrawlBatch permissionsCrawl(SlackWebApiClient client) {
        requireWorkingCredential(client);
        Crawl crawl = new Crawl();
        crawl.incomplete();
        Map<String, SlackUser> usersById = users(client);
        List<JsonNode> channels = channels(client, crawl);

        int failed = 0;
        for (JsonNode channel : channels) {
            try {
                observeMembership(client, channel, usersById, crawl);
            } catch (SlackApiException failure) {
                log.warn("Slack channel {} membership was skipped: {}",
                        channel.path("id").asString(""), failure.getMessage());
                failed++;
            }
        }
        abortIfMostlyFailed(failed, channels.size());

        crawl.grantKnownObjects(
                objects.activeObjectIds(
                        properties.organizationId(), "slack", properties.connectionKey()));

        return new ConnectorCrawlBatch(
                properties.organizationId(),
                "slack",
                properties.connectionKey(),
                properties.knowledgeSpaceId(),
                properties.actorUserId(),
                crawlCursor(crawl),
                ConnectorContractVersions.supported(),
                crawl.identities(),
                List.of(),
                crawl.permissions,
                List.of(),
                false);
    }

    private void observeMembership(
            SlackWebApiClient client, JsonNode channel, Map<String, SlackUser> usersById, Crawl crawl) {
        String channelId = channel.path("id").asString("");
        List<String> memberIds = client
                .collectPaged("conversations.members", Map.of("channel", channelId), "members")
                .stream()
                .map(JsonNode::asString)
                .filter(usersById::containsKey)
                .toList();
        memberIds.forEach(memberId -> crawl.observe(usersById.get(memberId)));
        crawl.observeChannel(channelId, channel.path("name").asString(channelId), memberIds);
    }

    private ConnectorCrawlBatch crawl(SlackWebApiClient client) {
        requireWorkingCredential(client);
        Crawl crawl = new Crawl();
        Map<String, SlackUser> usersById = users(client);
        List<JsonNode> channels = channels(client, crawl);

        int failed = 0;
        for (JsonNode channel : channels) {
            try {
                crawlChannel(client, channel, usersById, crawl);
            } catch (SlackApiException failure) {
                // A channel the bot cannot read is not a channel that vanished. Losing one costs
                // this crawl its completeness claim rather than costing the workspace its index.
                log.warn("Slack channel {} was skipped: {}", channel.path("id").asString(""), failure.getMessage());
                crawl.incomplete();
                failed++;
            }
        }
        abortIfMostlyFailed(failed, channels.size());

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
     * Checks the token before the crawl spends anything on it. A dead credential otherwise shows
     * up as every channel failing at once, which reads like a workspace problem; {@code auth.test}
     * costs one call and says plainly which it is.
     */
    private void requireWorkingCredential(SlackWebApiClient client) {
        try {
            client.call("auth.test", Map.of());
        } catch (SlackApiException refused) {
            String error = refused.errorCode() == null ? "" : refused.errorCode();
            throw new SlackCredentialUnavailableException(
                    "The Slack bot token for connection " + properties.connectionKey()
                            + " was rejected (" + error + ")");
        }
    }

    /**
     * Stops a run in which most channels failed rather than reporting it as a crawl.
     *
     * <p>Nothing is destroyed by carrying on — the completeness claim is already withdrawn, so
     * pruning cannot fire — but a batch would be checkpointed and the connection would look
     * healthy while its index quietly went stale. Failing instead sends the driver down its
     * transient path, which retries and then leaves the work for the next poll.
     */
    private static void abortIfMostlyFailed(int failed, int total) {
        if (failed > 0 && failed >= total * FAILED_CHANNEL_ABORT_SHARE) {
            throw new SlackApiException(
                    "Slack crawl abandoned: " + failed + " of " + total + " channels could not be read");
        }
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
                    || member.path("is_app_user").asBoolean(false)
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

    /**
     * The whole thread a history entry belongs to, or the message alone when it starts no thread.
     *
     * <p>{@code thread_ts} is present both on a thread's parent and on a reply broadcast back to
     * the channel, and in either case it names the root. Resolving through it rather than through
     * the entry's own timestamp matters because history returns newest first, so a broadcast
     * reply is seen before its parent — keying off the entry itself would index the reply alone
     * and then discard the real thread as a duplicate.
     */
    private List<JsonNode> thread(SlackWebApiClient client, String channelId, JsonNode entry) {
        String threadTs = entry.path("thread_ts").asString("");
        if (threadTs.isBlank() && entry.path("reply_count").asInt(0) <= 0) {
            return List.of(entry);
        }
        String rootTs = threadTs.isBlank() ? entry.path("ts").asString("") : threadTs;
        return client.collectPaged(
                "conversations.replies", Map.of("channel", channelId, "ts", rootTs), "messages");
    }

    /**
     * Joins, leaves, and channel housekeeping say nothing about the work. Automated posts are
     * dropped too, and an app can announce itself through either {@code bot_id} or {@code app_id}
     * — checking only the first lets integration chatter into the index.
     */
    private static boolean isIgnorable(JsonNode message) {
        return IGNORED_SUBTYPES.contains(message.path("subtype").asString(""))
                || !message.path("bot_id").asString("").isEmpty()
                || !message.path("app_id").asString("").isEmpty()
                || message.path("text").asString("").isBlank();
    }

    /**
     * A cursor that changes when the crawl's result changes and not otherwise. The producer owns
     * its meaning; what this one needs is that an unchanged workspace does not look like new work,
     * because the driver skips a cursor it has already completed.
     *
     * <p>Membership is part of the material, not just content. A permissions crawl carries no
     * content at all, so hashing content alone would give every one of them the same cursor and
     * the first would be the last ever ingested.
     */
    private static String crawlCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        crawl.contents.forEach(content ->
                material.append(content.externalObjectId()).append('=').append(content.contentRevision()).append(';'));
        crawl.observedChannels.forEach((channelId, channel) ->
                material.append(channelId).append('@').append(channel.memberIds()).append(';'));
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
        private final Set<String> seenObjectIds = new LinkedHashSet<>();
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

        /**
         * Re-states each known object's grant against the channel it belongs to. An object id is
         * {@code channelId__threadTs}, so the channel is read off the id rather than looked up.
         *
         * <p>An object whose channel this crawl did not see gets no entry at all. Emitting an
         * empty grant list would be an assertion that nobody may read it, and the crawl has no
         * grounds for that: the channel may simply have been unreadable this time.
         */
        private void grantKnownObjects(List<String> knownObjectIds) {
            for (String externalObjectId : knownObjectIds) {
                int separator = externalObjectId.indexOf("__");
                if (separator <= 0) {
                    continue;
                }
                String channelId = externalObjectId.substring(0, separator);
                if (!observedChannels.containsKey(channelId)) {
                    continue;
                }
                permissions.add(new ConnectorPermissionItem(
                        externalObjectId,
                        List.of(new ConnectorAclGrant(
                                SourcePrincipalKind.SOURCE_GROUP, channelId, AccessGate.ALLOW))));
            }
        }

        private void addThread(
                String channelId, String channelName, List<JsonNode> messages, Map<String, SlackUser> usersById) {
            if (messages.isEmpty()) {
                return;
            }
            JsonNode root = messages.getFirst();
            String threadTs = root.path("thread_ts").asString("").isBlank()
                    ? root.path("ts").asString("")
                    : root.path("thread_ts").asString("");
            String externalObjectId = channelId + "__" + threadTs;
            // A reply broadcast back to the channel appears in history alongside its own thread
            // root, and resolving either one yields the same thread. Without this the batch would
            // carry the same object twice.
            if (!seenObjectIds.add(externalObjectId)) {
                return;
            }
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

        /**
         * The thread as a reader would see it. Mentions and links are resolved out of Slack's
         * markup first: {@code <@U024BE7LH>} is noise in a full-text index and worse in an
         * embedding, where an opaque id cannot match the name somebody would actually ask about.
         */
        private String render(List<JsonNode> messages, Map<String, SlackUser> usersById) {
            Map<String, String> displayNames = new LinkedHashMap<>();
            usersById.forEach((id, user) -> displayNames.put(id, user.displayName()));
            StringBuilder rendered = new StringBuilder();
            for (JsonNode message : messages) {
                if (isIgnorable(message)) {
                    continue;
                }
                String text = SlackTextCleaner.clean(message.path("text").asString(""), displayNames);
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
