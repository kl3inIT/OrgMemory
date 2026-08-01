package com.orgmemory.connectors.slack;

import com.orgmemory.core.knowledge.acl.SourcePrincipalKind;

import com.orgmemory.connectors.PollingConnectorBatchSource;
import com.orgmemory.core.knowledge.connector.ConnectorAclGrant;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.connector.ConnectorComponentState;
import com.orgmemory.core.knowledge.connector.ConnectorContentItem;
import com.orgmemory.core.knowledge.connector.ConnectorContractVersions;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlConfiguration;
import com.orgmemory.core.knowledge.connector.ConnectorObjectDirectory;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.connector.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.connector.ConnectorMembershipItem;
import com.orgmemory.core.knowledge.connector.ConnectorMembershipMember;
import com.orgmemory.core.knowledge.connector.ConnectorPermissionItem;
import com.orgmemory.core.knowledge.connector.ConnectorCaptureStatus;
import com.orgmemory.core.knowledge.connector.ConnectorSyncComponent;
import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Crawls the Slack workspaces an administrator has enabled into the crawl-batch contract the
 * governed ledger already ingests. Nothing downstream changes: this only replaces the fixture
 * producer.
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
class SlackConnectorBatchSource extends PollingConnectorBatchSource<SlackWebApiClient> {

    private static final Logger log = LoggerFactory.getLogger(SlackConnectorBatchSource.class);
    private static final String SOURCE_SYSTEM = "slack";
    private static final String ALL_CHANNEL_TYPES = "public_channel,private_channel";
    private static final String PUBLIC_CHANNELS_ONLY = "public_channel";
    /** Housekeeping events Slack records as messages. They say nothing about the work. */
    private static final Set<String> IGNORED_SUBTYPES = Set.of(
            "channel_join", "channel_leave", "channel_topic", "channel_purpose", "channel_name",
            "channel_archive", "channel_unarchive", "channel_posting_permissions",
            "group_join", "group_leave", "group_archive", "group_unarchive",
            "pinned_item", "unpinned_item", "ekm_access_denied");

    /** Slack's word for "the bot is not in this channel", which is a scope rather than a fault. */
    private static final String NOT_IN_CHANNEL = "not_in_channel";

    private final ConnectorObjectDirectory objects;
    private final RestClient.Builder restClientBuilder;

    SlackConnectorBatchSource(
            ConnectorConnectionDirectory connections,
            ConnectorObjectDirectory objects,
            RestClient.Builder restClientBuilder) {
        this(connections, objects, restClientBuilder, Clock.systemUTC());
    }

    SlackConnectorBatchSource(
            ConnectorConnectionDirectory connections,
            ConnectorObjectDirectory objects,
            RestClient.Builder restClientBuilder,
            Clock clock) {
        super(connections, clock);
        this.objects = objects;
        this.restClientBuilder = restClientBuilder;
    }

    /**
     * One batch per enabled connection, asked for fresh each poll so that enabling a workspace,
     * repointing it at another Space, or replacing its token takes effect on the next poll rather
     * than on the next restart.
     *
     * <p>A connection that cannot produce is skipped rather than allowed to end the poll. One
     * workspace being rate limited, missing a token, or refusing a scope says nothing about the
     * others, and because nothing is checkpointed for a connection that produced no batch, the
     * only cost of skipping it is that it is tried again next time.
     *
     * <p>Skipped is reported, not swallowed. A missing token and a workspace with nothing new
     * both produce no batch, and the driver has to be able to tell them apart — that difference
     * is the whole answer to why a connection is indexing nothing.
     */
    /**
     * Slack's own word for what went wrong, where it gave one. A missing credential is this
     * side's own condition rather than Slack's, so it gets a name in the same shape instead of
     * borrowing one Slack would never send.
     */
    @Override
    protected String errorCodeOf(RuntimeException failure) {
        if (failure instanceof SlackApiException refused && refused.errorCode() != null) {
            return refused.errorCode();
        }
        return failure instanceof SlackCredentialUnavailableException ? "no_credential" : "slack_error";
    }

    /**
     * What one connection owes this poll, and which kind depends on what is due.
     *
     * <p>Access changes daily and content rarely, so re-reading every message body to answer "who
     * can see this now" is the expensive mistake. Between content crawls this produces a
     * permissions crawl instead: channels and their members, applied to the objects the ledger
     * already holds. That costs a call per channel rather than a call per thread. When content is
     * due — because the interval elapsed or an administrator asked — the whole thread is read.
     *
     * <p>The cadence advances only once the crawl has produced a batch. Advancing it first spends
     * the interval on an attempt that failed: a workspace briefly unreachable, or a bot not yet
     * invited anywhere, would quietly downgrade every poll for the next hour to permissions only,
     * and the connection would look busy while nothing was being read.
     */
    ConnectorCrawlBatch batchFor(ConnectorCrawlConfiguration configuration) {
        return pollConnection(configuration);
    }

    @Override
    protected String sourceSystem() {
        return SOURCE_SYSTEM;
    }

    @Override
    protected String displayName() {
        return "Slack";
    }

    @Override
    protected String crawlCursorPrefix() {
        return "slack-";
    }

    @Override
    protected SlackWebApiClient createClient(
            ConnectorCrawlConfiguration configuration, SecretValue credential) {
        return new SlackWebApiClient(restClientBuilder, credential.expose());
    }

    @Override
    protected RuntimeException missingCredential(ConnectorCrawlConfiguration configuration) {
        return new SlackCredentialUnavailableException(
                "No Slack credential is stored for connection " + configuration.sourceConnectionKey());
    }

    @Override
    protected boolean isExpectedConnectorFailure(RuntimeException failure) {
        return failure instanceof SlackCredentialUnavailableException
                || failure instanceof SlackApiException;
    }

    @Override
    protected CrawlResult crawl(
            SlackWebApiClient client,
            ConnectorCrawlConfiguration configuration,
            boolean contentDue) {
        return contentDue
                ? contentCrawl(client, configuration)
                : permissionsCrawl(client, configuration);
    }

    /**
     * Who may currently read what, without reading anything. Channels and their members come from
     * Slack; the objects those grants apply to come from the ledger, because asking Slack to
     * enumerate them again would mean paging every channel's history for ids we already have.
     *
     * <p>This batch never claims content-enumeration completeness, including when every permission
     * read succeeds, and the reason is worth stating plainly: its object list is our own record
     * rather than the source's. The literal {@code complete=false} passed to {@link #batch} below
     * preserves that invariant; do not replace it with {@code crawl.complete}. A crawl that
     * claimed to have enumerated the connection on the ledger's own evidence would be confirming
     * itself, and the ledger would then be entitled to retire anything the circular answer left
     * out.
     */
    private CrawlResult permissionsCrawl(
            SlackWebApiClient client, ConnectorCrawlConfiguration configuration) {
        requireWorkingCredential(client, configuration);
        SlackCrawlSettings settings = SlackCrawlSettings.from(configuration.sourceConfig());
        Crawl crawl = new Crawl();
        Map<String, SlackUser> usersById = users(client);
        List<JsonNode> channels = channels(client, settings, crawl);

        int failed = 0;
        for (JsonNode channel : channels) {
            try {
                observeMembership(client, channel, usersById, crawl);
            } catch (SlackApiException failure) {
                log.warn("Slack channel {} membership was skipped: {}",
                        channel.path("id").asString(""), failure.getMessage());
                crawl.accessIncomplete();
                if (!NOT_IN_CHANNEL.equals(failure.errorCode())) {
                    failed++;
                }
            }
        }
        crawl.grantKnownObjects(objects.activeObjectIds(
                configuration.organizationId(), SOURCE_SYSTEM, configuration.sourceConnectionKey()));

        return result(
                batch(configuration, crawl, List.of(), false, false),
                failed,
                channels.size(),
                "channels");
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

    private CrawlResult contentCrawl(
            SlackWebApiClient client, ConnectorCrawlConfiguration configuration) {
        requireWorkingCredential(client, configuration);
        SlackCrawlSettings settings = SlackCrawlSettings.from(configuration.sourceConfig());
        Crawl crawl = new Crawl();
        Map<String, SlackUser> usersById = users(client);
        Map<String, String> displayNames = new LinkedHashMap<>();
        usersById.forEach((id, user) -> displayNames.put(id, user.displayName()));
        List<JsonNode> channels = channels(client, settings, crawl);

        int failed = 0;
        for (JsonNode channel : channels) {
            try {
                crawlChannel(client, channel, usersById, displayNames, settings, crawl);
            } catch (SlackApiException failure) {
                // A channel the bot cannot read is not a channel that vanished. Losing one costs
                // this crawl its completeness claim rather than costing the workspace its index.
                log.warn("Slack channel {} was skipped: {}", channel.path("id").asString(""), failure.getMessage());
                crawl.incomplete();
                crawl.accessIncomplete();
                if (!NOT_IN_CHANNEL.equals(failure.errorCode())) {
                    // Membership can change between listing the channels and reading one. That
                    // says nothing about the workspace being reachable, so it must not push this
                    // crawl toward being abandoned.
                    failed++;
                }
            }
        }
        return result(
                batch(configuration, crawl, crawl.contents, true, crawl.complete),
                failed,
                channels.size(),
                "channels");
    }

    private ConnectorCrawlBatch batch(
            ConnectorCrawlConfiguration configuration,
            Crawl crawl,
            List<ConnectorContentItem> contents,
            boolean contentIncluded,
            boolean complete) {
        List<ConnectorComponentState> componentStates = new ArrayList<>();
        if (contentIncluded) {
            componentStates.add(ConnectorComponentState.complete(
                    ConnectorSyncComponent.CONTENT, contentCursor(crawl)));
        }
        componentStates.add(crawl.accessComplete
                ? ConnectorComponentState.complete(
                        ConnectorSyncComponent.PERMISSION, permissionCursor(crawl))
                : ConnectorComponentState.incomplete(
                        ConnectorSyncComponent.PERMISSION,
                        permissionCursor(crawl),
                        "SLACK_ACCESS_NOT_FULLY_READ"));
        componentStates.add(crawl.accessComplete
                ? ConnectorComponentState.complete(
                        ConnectorSyncComponent.MEMBERSHIP, membershipCursor(crawl))
                : ConnectorComponentState.incomplete(
                        ConnectorSyncComponent.MEMBERSHIP,
                        membershipCursor(crawl),
                        "SLACK_ACCESS_NOT_FULLY_READ"));
        return new ConnectorCrawlBatch(
                configuration.organizationId(),
                SOURCE_SYSTEM,
                configuration.sourceConnectionKey(),
                configuration.knowledgeSpaceId(),
                configuration.actorUserId(),
                crawlCursor(componentStates),
                ConnectorContractVersions.supported(),
                crawl.identities(),
                crawl.memberships(),
                contents,
                crawl.permissions,
                List.of(),
                componentStates,
                complete);
    }

    /**
     * Checks the token before the crawl spends anything on it. A dead credential otherwise shows
     * up as every channel failing at once, which reads like a workspace problem; {@code auth.test}
     * costs one call and says plainly which it is.
     */
    private static void requireWorkingCredential(
            SlackWebApiClient client, ConnectorCrawlConfiguration configuration) {
        try {
            client.call("auth.test", Map.of());
        } catch (SlackApiException refused) {
            String error = refused.errorCode() == null ? "" : refused.errorCode();
            throw new SlackCredentialUnavailableException(
                    "The Slack bot token for connection " + configuration.sourceConnectionKey()
                            + " was rejected (" + error + ")");
        }
    }

    /**
     * Every human account in the workspace, indexed by id. Bots and deactivated accounts are
     * dropped: neither can be a person to map to, and a bot has no address to map by.
     */
    private static Map<String, SlackUser> users(SlackWebApiClient client) {
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
    private static List<JsonNode> channels(
            SlackWebApiClient client, SlackCrawlSettings settings, Crawl crawl) {
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
            crawl.accessIncomplete();
            channels = client.collectPaged(
                    "conversations.list",
                    Map.of("types", PUBLIC_CHANNELS_ONLY, "exclude_archived", "true"),
                    "channels");
        }
        if (!settings.channels().isEmpty()) {
            // A configured subset cannot speak for what it was never asked to look at.
            crawl.incomplete();
            Set<String> wanted = Set.copyOf(settings.channels());
            channels = channels.stream()
                    .filter(channel -> wanted.contains(channel.path("id").asString(""))
                            || wanted.contains(channel.path("name").asString("")))
                    .toList();
            channels.forEach(channel -> joinIfChosen(client, channel));
        }
        return joinedOnly(channels, crawl);
    }

    /**
     * Adds the bot to a chosen channel it is not yet in.
     *
     * <p>Only for channels an administrator named. Choosing a channel here is the instruction to
     * read it, and requiring a separate trip to Slack to repeat that instruction is a step that
     * carries no decision. It also repairs itself: a bot removed from a channel that is still
     * configured rejoins on the next crawl rather than leaving a silent gap.
     *
     * <p>Nothing is chosen implicitly. A connection with no channels configured joins nothing and
     * reads what it was invited to, so a deployment that would rather grant no write scope simply
     * does not use the picker — and an app installed without {@code channels:join} refuses here,
     * which costs the completeness claim rather than the crawl.
     */
    private static void joinIfChosen(SlackWebApiClient client, JsonNode channel) {
        if (channel.path("is_member").asBoolean(true)) {
            return;
        }
        String id = channel.path("id").asString("");
        try {
            client.call("conversations.join", Map.of("channel", id));
            log.info("Joined Slack channel {} because it is configured to be crawled", id);
            // conversations.list said otherwise a moment ago; it is a member now.
            ((ObjectNode) channel).put("is_member", true);
        } catch (SlackApiException refused) {
            // A private channel, or an app without channels:join. Either way somebody at the
            // source has to act, and joinedOnly will withdraw the completeness claim for it.
            log.warn("Could not join Slack channel {}: {}", id, refused.errorCode());
        }
    }

    /**
     * Only the channels the bot was actually invited to.
     *
     * <p>A channel the bot is not in is not a failure to read one. Inviting the bot is how an
     * administrator says which channels to crawl, so being outside the rest is the configuration
     * working. Asking Slack for their history anyway spends a call to be told {@code
     * not_in_channel}, and — because that read like a failure — a workspace where the bot had been
     * invited to a minority of its channels could never finish a crawl at all: the threshold
     * abort fired on the normal case it was never meant to catch.
     *
     * <p>They do cost the completeness claim. They exist in this connection and were not read, so
     * their objects must not be retired for going unmentioned.
     *
     * <p>Only an explicit {@code false} excludes a channel. Slack lists a private channel only to
     * an app that is in it, and does not always restate the flag there; absent means try it, and
     * a genuine refusal is still caught.
     */
    private static List<JsonNode> joinedOnly(List<JsonNode> channels, Crawl crawl) {
        List<JsonNode> joined = channels.stream()
                .filter(channel -> channel.path("is_member").asBoolean(true))
                .toList();
        if (joined.size() != channels.size()) {
            crawl.incomplete();
        }
        return joined;
    }

    private void crawlChannel(
            SlackWebApiClient client,
            JsonNode channel,
            Map<String, SlackUser> usersById,
            Map<String, String> displayNames,
            SlackCrawlSettings settings,
            Crawl crawl) {
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
            if (threads++ >= settings.maxThreadsPerChannel()) {
                log.warn("Slack channel {} exceeded {} threads; the crawl no longer covers it fully",
                        channelName, settings.maxThreadsPerChannel());
                crawl.incomplete();
                break;
            }
            crawl.addThread(
                    channelId,
                    channelName,
                    thread(client, channelId, message),
                    usersById,
                    displayNames);
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
    private static List<JsonNode> thread(SlackWebApiClient client, String channelId, JsonNode entry) {
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
    private static String contentCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        crawl.contents.forEach(content ->
                material.append(content.externalObjectId()).append('=').append(content.contentRevision()).append(';'));
        material.append("complete=").append(crawl.complete);
        return "slack-content-" + sha256(material.toString());
    }

    private static String permissionCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        crawl.permissions.stream()
                .sorted(java.util.Comparator.comparing(ConnectorPermissionItem::externalObjectId))
                .forEach(permission -> material
                        .append(permission.externalObjectId())
                        .append('@')
                        .append(permission.grants())
                        .append(';'));
        material.append("enumerationComplete=").append(crawl.complete);
        material.append("accessComplete=").append(crawl.accessComplete);
        return "slack-permission-" + sha256(material.toString());
    }

    private static String membershipCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        crawl.observedChannels.forEach((channelId, channel) ->
                material.append(channelId).append('@').append(channel.memberIds()).append(';'));
        material.append("enumerationComplete=").append(crawl.complete);
        material.append("accessComplete=").append(crawl.accessComplete);
        return "slack-membership-" + sha256(material.toString());
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
        return com.orgmemory.core.shared.Digests.sha256(value);
    }

    private record SlackUser(String id, String email, String displayName) {
    }

    /** Accumulates one crawl's payloads and whether it may still claim to have seen everything. */
    private static final class Crawl {

        private final Map<String, SlackUser> observedUsers = new LinkedHashMap<>();
        private final Map<String, ChannelIdentity> observedChannels = new LinkedHashMap<>();
        private final List<ConnectorContentItem> contents = new ArrayList<>();
        private final List<ConnectorPermissionItem> permissions = new ArrayList<>();
        private final Set<String> seenObjectIds = new LinkedHashSet<>();
        private boolean complete = true;
        private boolean accessComplete = true;

        private void incomplete() {
            complete = false;
        }

        private void accessIncomplete() {
            accessComplete = false;
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
                String channelId,
                String channelName,
                List<JsonNode> messages,
                Map<String, SlackUser> usersById,
                Map<String, String> displayNames) {
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
            String body = render(messages, usersById, displayNames);
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
        private String render(
                List<JsonNode> messages,
                Map<String, SlackUser> usersById,
                Map<String, String> displayNames) {
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
                        null));
            }
            for (ChannelIdentity channel : observedChannels.values()) {
                identities.add(new ConnectorIdentityItem(
                        SourcePrincipalKind.SOURCE_GROUP,
                        channel.id(),
                        null,
                        "#" + channel.name(),
                        false,
                        null,
                        null));
            }
            return identities;
        }

        private List<ConnectorMembershipItem> memberships() {
            List<ConnectorMembershipItem> memberships = new ArrayList<>();
            for (ChannelIdentity channel : observedChannels.values()) {
                Set<String> members = new LinkedHashSet<>(channel.memberIds());
                members.retainAll(observedUsers.keySet());
                memberships.add(new ConnectorMembershipItem(
                        channel.id(),
                        ConnectorCaptureStatus.COMPLETE,
                        null,
                        members.stream()
                                .map(memberId -> new ConnectorMembershipMember(
                                        SourcePrincipalKind.SOURCE_USER, memberId))
                                .toList()));
            }
            return memberships;
        }
    }

    private record ChannelIdentity(String id, String name, List<String> memberIds) {
    }
}
