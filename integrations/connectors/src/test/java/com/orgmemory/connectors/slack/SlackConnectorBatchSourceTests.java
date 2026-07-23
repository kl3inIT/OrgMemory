package com.orgmemory.connectors.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.ConnectorContentItem;
import com.orgmemory.core.knowledge.ConnectorObjectDirectory;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.SourcePrincipalKind;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Drives the adapter against recorded Slack responses. The shapes are Slack's; no network is
 * involved. What matters most here is not that a crawl produces content — it is when the crawl
 * is willing to call itself complete, because that claim is what lets the ledger retire
 * everything the crawl left out.
 */
class SlackConnectorBatchSourceTests {

    private static final UUID ORG = UUID.fromString("aa000000-0000-4000-8000-000000000001");
    private static final UUID SPACE = UUID.fromString("aa000000-0000-4000-8000-000000000002");
    private static final UUID ACTOR = UUID.fromString("aa000000-0000-4000-8000-000000000003");
    private static final String CONNECTION = "T-workspace";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private ConnectorObjectDirectory directory;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        directory = mock(ConnectorObjectDirectory.class);
        when(directory.activeObjectIds(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void turnsAWorkspaceIntoTheCrawlContract() {
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();

        ConnectorCrawlBatch batch = crawl(List.of());

        assertEquals("slack", batch.sourceSystem());
        assertEquals(CONNECTION, batch.sourceConnectionKey());
        assertEquals(ORG, batch.organizationId());

        ConnectorContentItem thread = batch.contents().getFirst();
        assertEquals("C-eng__1700000001.000100", thread.externalObjectId(),
                "a thread is keyed by its channel and root timestamp");
        assertEquals("#engineering", thread.title());
        assertTrue(thread.body().contains("Mai: The deploy window is Thursday"));
        assertTrue(thread.body().contains("Lan: Confirmed, I will run the rollback drill"),
                "a thread carries its replies, not just the root");

        assertEquals(1, batch.permissions().size());
        assertEquals("C-eng", batch.permissions().getFirst().grants().getFirst().principalExternalKey());
    }

    @Test
    void reportsMembersAsUsersAndTheChannelAsTheirGroup() {
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();

        ConnectorCrawlBatch batch = crawl(List.of());

        List<ConnectorIdentityItem> users = batch.identities().stream()
                .filter(identity -> identity.kind() == SourcePrincipalKind.SOURCE_USER)
                .toList();
        assertEquals(List.of("U-mai", "U-lan"), users.stream().map(ConnectorIdentityItem::externalKey).toList());
        assertEquals("mai@example.com", users.getFirst().email());
        assertTrue(users.getFirst().ssoVerified(),
                "Slack confirms address ownership before an account exists, so it vouches");

        ConnectorIdentityItem channel = batch.identities().stream()
                .filter(identity -> identity.kind() == SourcePrincipalKind.SOURCE_GROUP)
                .findFirst()
                .orElseThrow();
        assertEquals("C-eng", channel.externalKey());
        assertEquals(List.of("U-mai", "U-lan"), channel.memberExternalKeys());
    }

    @Test
    void dropsBotsDeactivatedAccountsAndChannelNoise() {
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();

        ConnectorCrawlBatch batch = crawl(List.of());

        assertTrue(
                batch.identities().stream().noneMatch(identity -> identity.externalKey().equals("U-bot")),
                "a bot has no person to map to");
        assertTrue(
                batch.identities().stream().noneMatch(identity -> identity.externalKey().equals("U-gone")),
                "a deactivated account is not a member to grant through");
        assertEquals(1, batch.contents().size(), "a join message is not a thread worth indexing");
    }

    @Test
    void claimsCompletenessOnlyForAnUnfilteredUninterruptedCrawl() {
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();

        assertTrue(crawl(List.of()).crawlComplete(), "an unfiltered crawl that saw everything may say so");
    }

    @Test
    void withdrawsTheCompletenessClaimWhenOnlySomeChannelsWereAskedFor() {
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();

        ConnectorCrawlBatch batch = crawl(List.of("engineering"));

        assertFalse(
                batch.crawlComplete(),
                "a crawl told to look at one channel cannot speak for the others");
    }

    @Test
    void withdrawsTheCompletenessClaimWhenAChannelCouldNotBeRead() {
        expectAuth();
        expectUsers();
        server.expect(requestTo("https://slack.com/api/conversations.list"))
                .andRespond(withSuccess(THREE_CHANNELS_JSON, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://slack.com/api/conversations.members"))
                .andRespond(withSuccess("{\"ok\":false,\"error\":\"not_in_channel\"}", MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.manyTimes(), requestTo("https://slack.com/api/conversations.members"))
                .andRespond(withSuccess(MEMBERS_JSON, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.manyTimes(), requestTo("https://slack.com/api/conversations.history"))
                .andRespond(withSuccess(HISTORY_JSON, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.manyTimes(), requestTo("https://slack.com/api/conversations.replies"))
                .andRespond(withSuccess(REPLIES_JSON, MediaType.APPLICATION_JSON));

        ConnectorCrawlBatch batch = crawl(List.of());

        assertFalse(batch.crawlComplete(), "a channel that could not be read is not a channel that vanished");
        assertFalse(batch.contents().isEmpty(), "the readable channels still arrive");
    }

    @Test
    void abandonsARunInWhichMostChannelsCouldNotBeRead() {
        expectAuth();
        expectUsers();
        server.expect(requestTo("https://slack.com/api/conversations.list"))
                .andRespond(withSuccess(THREE_CHANNELS_JSON, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.manyTimes(), requestTo("https://slack.com/api/conversations.members"))
                .andRespond(withSuccess("{\"ok\":false,\"error\":\"not_in_channel\"}", MediaType.APPLICATION_JSON));

        SlackApiException abandoned = org.junit.jupiter.api.Assertions.assertThrows(
                SlackApiException.class, () -> crawl(List.of()));

        assertTrue(
                abandoned.getMessage().contains("3 of 3"),
                () -> "unexpected message: " + abandoned.getMessage());
    }

    @Test
    void refusesToCrawlWithACredentialSlackRejects() {
        server.expect(requestTo("https://slack.com/api/auth.test"))
                .andRespond(withSuccess("{\"ok\":false,\"error\":\"invalid_auth\"}", MediaType.APPLICATION_JSON));

        SlackCredentialUnavailableException rejected = org.junit.jupiter.api.Assertions.assertThrows(
                SlackCredentialUnavailableException.class, () -> crawl(List.of()));

        assertTrue(rejected.getMessage().contains("invalid_auth"));
        assertFalse(rejected.getMessage().contains("xoxb-"), "a failure must not carry the token");
    }

    @Test
    void withdrawsTheCompletenessClaimWhenPrivateChannelsAreOutOfScope() {
        expectAuth();
        expectUsers();
        server.expect(requestTo("https://slack.com/api/conversations.list"))
                .andRespond(withSuccess("{\"ok\":false,\"error\":\"missing_scope\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://slack.com/api/conversations.list"))
                .andRespond(withSuccess(CHANNELS_JSON, MediaType.APPLICATION_JSON));
        expectMembers();
        expectHistory();

        ConnectorCrawlBatch batch = crawl(List.of());

        assertFalse(batch.crawlComplete(), "public channels alone do not account for the private ones");
        assertFalse(batch.contents().isEmpty(), "the crawl still delivers what it could read");
    }

    @Test
    void producesTheSameCursorForAnUnchangedWorkspaceAndADifferentOneAfterAnEdit() {
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();
        String first = crawl(List.of()).crawlCursor();

        setUp();
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();
        assertEquals(first, crawl(List.of()).crawlCursor(), "an unchanged workspace is not new work");

        setUp();
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        server.expect(requestTo("https://slack.com/api/conversations.history"))
                .andRespond(withSuccess(HISTORY_JSON.replace("Thursday", "Friday"), MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.manyTimes(), requestTo("https://slack.com/api/conversations.replies"))
                .andRespond(withSuccess(REPLIES_JSON.replace("Thursday", "Friday"), MediaType.APPLICATION_JSON));

        assertNotEquals(first, crawl(List.of()).crawlCursor(), "an edit is new work");
    }

    @Test
    void indexesAThreadOnceWhenAReplyWasBroadcastBackToTheChannel() {
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        // Slack returns newest first, so the broadcast reply arrives before the parent it belongs to.
        server.expect(requestTo("https://slack.com/api/conversations.history"))
                .andRespond(withSuccess(BROADCAST_HISTORY_JSON, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.manyTimes(), requestTo("https://slack.com/api/conversations.replies"))
                .andRespond(withSuccess(REPLIES_JSON, MediaType.APPLICATION_JSON));

        ConnectorCrawlBatch batch = crawl(List.of());

        assertEquals(1, batch.contents().size(), "one thread is one object however often it surfaces");
        assertEquals("C-eng__1700000001.000100", batch.contents().getFirst().externalObjectId());
        assertTrue(
                batch.contents().getFirst().body().contains("Mai: The deploy window is Thursday"),
                "resolving through thread_ts keeps the whole thread, not the broadcast reply alone");
    }

    @Test
    void resolvesMentionsAndLinksOutOfTheIndexedBody() {
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        server.expect(requestTo("https://slack.com/api/conversations.history"))
                .andRespond(withSuccess(MARKUP_HISTORY_JSON, MediaType.APPLICATION_JSON));

        String body = crawl(List.of()).contents().getFirst().body();

        assertTrue(body.contains("@Lan"), () -> "a mention should read as a name: " + body);
        assertFalse(body.contains("<@U-lan>"), () -> "raw markup reached the index: " + body);
        assertFalse(body.contains("U-lan"), () -> "an opaque id reached the index: " + body);
    }

    @Test
    void readsNoMessageBodiesBetweenContentCrawls() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-23T09:00:00Z"));
        SlackConnectorBatchSource source = source(List.of(), clock);
        when(directory.activeObjectIds(ORG, "slack", CONNECTION))
                .thenReturn(List.of("C-eng__1700000001.000100", "C-eng__1700000009.000100"));

        // First poll is due a content crawl and pays for it.
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();
        assertTrue(source.pendingBatches().getFirst().crawlComplete());
        server.verify();

        // Ten minutes later the interval has not elapsed, so only access is re-read.
        setUpServerOnly();
        clock.advance(Duration.ofMinutes(10));
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();

        ConnectorCrawlBatch permissions = source.pendingBatches().getFirst();

        // No history or replies expectation was registered, so asking for either would have failed.
        server.verify();
        assertTrue(permissions.contents().isEmpty(), "a permissions crawl carries no content");
        assertEquals(
                List.of("C-eng__1700000001.000100", "C-eng__1700000009.000100"),
                permissions.permissions().stream().map(p -> p.externalObjectId()).toList(),
                "it re-states the grants of objects the ledger already holds");
        assertEquals(
                "C-eng",
                permissions.permissions().getFirst().grants().getFirst().principalExternalKey());
    }

    @Test
    void aPermissionsCrawlNeverClaimsCompleteness() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-23T09:00:00Z"));
        SlackConnectorBatchSource source = source(List.of(), clock);
        when(directory.activeObjectIds(ORG, "slack", CONNECTION))
                .thenReturn(List.of("C-eng__1700000001.000100"));
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();
        source.pendingBatches();

        setUpServerOnly();
        clock.advance(Duration.ofMinutes(1));
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();

        assertFalse(
                source.pendingBatches().getFirst().crawlComplete(),
                "its object list is our own record, so claiming completeness would confirm itself");
    }

    @Test
    void aPermissionsCrawlLeavesOutObjectsWhoseChannelItCouldNotSee() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-23T09:00:00Z"));
        SlackConnectorBatchSource source = source(List.of(), clock);
        when(directory.activeObjectIds(ORG, "slack", CONNECTION))
                .thenReturn(List.of("C-eng__1700000001.000100", "C-vanished__1700000002.000100"));
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();
        source.pendingBatches();

        setUpServerOnly();
        clock.advance(Duration.ofMinutes(1));
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();

        ConnectorCrawlBatch permissions = source.pendingBatches().getFirst();

        assertEquals(
                List.of("C-eng__1700000001.000100"),
                permissions.permissions().stream().map(p -> p.externalObjectId()).toList(),
                "an empty grant list would assert nobody may read it, which this crawl cannot know");
    }

    @Test
    void reissuesAContentCrawlOnceTheIntervalElapses() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-23T09:00:00Z"));
        SlackConnectorBatchSource source = source(List.of(), clock);
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();
        source.pendingBatches();

        setUpServerOnly();
        clock.advance(Duration.ofHours(2));
        expectAuth();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();

        ConnectorCrawlBatch again = source.pendingBatches().getFirst();

        server.verify();
        assertFalse(again.contents().isEmpty(), "content is re-read once its interval has elapsed");
    }

    @Test
    void producesNothingUntilTheConnectionIsFullyConfigured() {
        SlackConnectorProperties unconfigured = new SlackConnectorProperties(
                false, "xoxb-token", CONNECTION, ORG, SPACE, ACTOR, List.of(), null, null);

        assertTrue(
                new SlackConnectorBatchSource(unconfigured, key -> "xoxb-token", directory, builder)
                        .pendingBatches()
                        .isEmpty(),
                "a disabled connection contacts Slack not at all");
    }

    @Test
    void surfacesARateLimitedWorkspaceRatherThanCrawlingPartially() {
        expectAuth();
        for (int attempt = 0; attempt < 4; attempt++) {
            server.expect(requestTo("https://slack.com/api/users.list"))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        }

        SlackApiException limited = org.junit.jupiter.api.Assertions.assertThrows(
                SlackApiException.class, () -> crawl(List.of()));

        assertEquals("ratelimited", limited.errorCode());
    }

    /** Re-arms the mock server between polls without discarding the source under test. */
    private void setUpServerOnly() {
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    }

    /** A clock the test moves, so a cadence can be proved without waiting for one. */
    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration amount) {
            now = now.plus(amount);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    private ConnectorCrawlBatch crawl(List<String> channels) {
        List<ConnectorCrawlBatch> batches = source(channels, Clock.systemUTC()).pendingBatches();
        assertEquals(1, batches.size());
        return batches.getFirst();
    }

    private SlackConnectorBatchSource source(List<String> channels, Clock clock) {
        SlackConnectorProperties properties = new SlackConnectorProperties(
                true, "xoxb-not-a-real-token", CONNECTION, ORG, SPACE, ACTOR, channels, null,
                Duration.ofHours(1));
        return new SlackConnectorBatchSource(
                properties, key -> "xoxb-not-a-real-token", directory, builder, clock);
    }

    private void expectAuth() {
        server.expect(requestTo("https://slack.com/api/auth.test"))
                .andRespond(withSuccess("{\"ok\":true,\"team_id\":\"T-workspace\"}", MediaType.APPLICATION_JSON));
    }

    private void expectUsers() {
        server.expect(requestTo("https://slack.com/api/users.list"))
                .andRespond(withSuccess(USERS_JSON, MediaType.APPLICATION_JSON));
    }

    private void expectChannels() {
        server.expect(requestTo("https://slack.com/api/conversations.list"))
                .andRespond(withSuccess(CHANNELS_JSON, MediaType.APPLICATION_JSON));
    }

    private void expectMembers() {
        server.expect(requestTo("https://slack.com/api/conversations.members"))
                .andRespond(withSuccess(MEMBERS_JSON, MediaType.APPLICATION_JSON));
    }

    private void expectHistory() {
        server.expect(requestTo("https://slack.com/api/conversations.history"))
                .andRespond(withSuccess(HISTORY_JSON, MediaType.APPLICATION_JSON));
        server.expect(ExpectedCount.manyTimes(), requestTo("https://slack.com/api/conversations.replies"))
                .andRespond(withSuccess(REPLIES_JSON, MediaType.APPLICATION_JSON));
    }

    private static final String USERS_JSON =
            """
            {"ok":true,"members":[
              {"id":"U-mai","real_name":"Mai Nguyen","name":"mai","deleted":false,"is_bot":false,
               "profile":{"email":"mai@example.com","display_name":"Mai"}},
              {"id":"U-lan","real_name":"Lan Tran","name":"lan","deleted":false,"is_bot":false,
               "profile":{"email":"lan@example.com","display_name":"Lan"}},
              {"id":"U-bot","real_name":"Deploy Bot","name":"deploybot","deleted":false,"is_bot":true,
               "profile":{"email":"","display_name":"Deploy Bot"}},
              {"id":"U-gone","real_name":"Former Person","name":"gone","deleted":true,"is_bot":false,
               "profile":{"email":"gone@example.com","display_name":"Former"}}
            ]}
            """;

    private static final String THREE_CHANNELS_JSON =
            """
            {"ok":true,"channels":[
              {"id":"C-eng","name":"engineering","is_private":false},
              {"id":"C-ops","name":"operations","is_private":false},
              {"id":"C-sales","name":"sales","is_private":false}]}
            """;

    private static final String CHANNELS_JSON =
            """
            {"ok":true,"channels":[{"id":"C-eng","name":"engineering","is_private":false}]}
            """;

    private static final String MEMBERS_JSON =
            """
            {"ok":true,"members":["U-mai","U-lan","U-bot"]}
            """;

    private static final String HISTORY_JSON =
            """
            {"ok":true,"messages":[
              {"type":"message","user":"U-mai","ts":"1700000001.000100","reply_count":1,
               "text":"The deploy window is Thursday and the rollback owner is the platform team"},
              {"type":"message","subtype":"channel_join","user":"U-lan","ts":"1700000000.000100",
               "text":"<@U-lan> has joined the channel"}
            ]}
            """;

    private static final String BROADCAST_HISTORY_JSON =
            """
            {"ok":true,"messages":[
              {"type":"message","subtype":"thread_broadcast","user":"U-lan","ts":"1700000002.000100",
               "thread_ts":"1700000001.000100","text":"Confirmed, I will run the rollback drill"},
              {"type":"message","user":"U-mai","ts":"1700000001.000100","thread_ts":"1700000001.000100",
               "reply_count":1,
               "text":"The deploy window is Thursday and the rollback owner is the platform team"}
            ]}
            """;

    private static final String MARKUP_HISTORY_JSON =
            """
            {"ok":true,"messages":[
              {"type":"message","user":"U-mai","ts":"1700000003.000100",
               "text":"<@U-lan> please read <https://wiki.example/rb|the runbook> in <#C-eng|engineering>"}
            ]}
            """;

    private static final String REPLIES_JSON =
            """
            {"ok":true,"messages":[
              {"type":"message","user":"U-mai","ts":"1700000001.000100","thread_ts":"1700000001.000100",
               "text":"The deploy window is Thursday and the rollback owner is the platform team"},
              {"type":"message","user":"U-lan","ts":"1700000002.000100","thread_ts":"1700000001.000100",
               "text":"Confirmed, I will run the rollback drill"}
            ]}
            """;
}
