package com.orgmemory.connectors.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.orgmemory.core.knowledge.ConnectorContentItem;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.SourcePrincipalKind;
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

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    }

    @Test
    void turnsAWorkspaceIntoTheCrawlContract() {
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
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();

        assertTrue(crawl(List.of()).crawlComplete(), "an unfiltered crawl that saw everything may say so");
    }

    @Test
    void withdrawsTheCompletenessClaimWhenOnlySomeChannelsWereAskedFor() {
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
        expectUsers();
        expectChannels();
        server.expect(requestTo("https://slack.com/api/conversations.members"))
                .andRespond(withSuccess("{\"ok\":false,\"error\":\"not_in_channel\"}", MediaType.APPLICATION_JSON));

        ConnectorCrawlBatch batch = crawl(List.of());

        assertFalse(batch.crawlComplete(), "a channel that could not be read is not a channel that vanished");
        assertTrue(batch.contents().isEmpty());
    }

    @Test
    void withdrawsTheCompletenessClaimWhenPrivateChannelsAreOutOfScope() {
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
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();
        String first = crawl(List.of()).crawlCursor();

        setUp();
        expectUsers();
        expectChannels();
        expectMembers();
        expectHistory();
        assertEquals(first, crawl(List.of()).crawlCursor(), "an unchanged workspace is not new work");

        setUp();
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
    void producesNothingUntilTheConnectionIsFullyConfigured() {
        SlackConnectorProperties unconfigured = new SlackConnectorProperties(
                false, "xoxb-token", CONNECTION, ORG, SPACE, ACTOR, List.of(), null);

        assertTrue(
                new SlackConnectorBatchSource(unconfigured, key -> "xoxb-token", builder)
                        .pendingBatches()
                        .isEmpty(),
                "a disabled connection contacts Slack not at all");
    }

    @Test
    void surfacesARateLimitedWorkspaceRatherThanCrawlingPartially() {
        for (int attempt = 0; attempt < 4; attempt++) {
            server.expect(requestTo("https://slack.com/api/users.list"))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "0"));
        }

        SlackApiException limited = org.junit.jupiter.api.Assertions.assertThrows(
                SlackApiException.class, () -> crawl(List.of()));

        assertEquals("ratelimited", limited.errorCode());
    }

    private ConnectorCrawlBatch crawl(List<String> channels) {
        SlackConnectorProperties properties = new SlackConnectorProperties(
                true, "xoxb-not-a-real-token", CONNECTION, ORG, SPACE, ACTOR, channels, null);
        List<ConnectorCrawlBatch> batches =
                new SlackConnectorBatchSource(properties, key -> "xoxb-not-a-real-token", builder).pendingBatches();
        assertEquals(1, batches.size());
        return batches.getFirst();
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
