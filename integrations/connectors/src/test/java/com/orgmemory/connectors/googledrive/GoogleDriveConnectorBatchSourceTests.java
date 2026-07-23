package com.orgmemory.connectors.googledrive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.orgmemory.core.knowledge.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorCrawlConfiguration;
import com.orgmemory.core.knowledge.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.ConnectorPoll;
import com.orgmemory.core.knowledge.SourcePrincipalKind;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Drives the adapter against recorded Drive responses. The shapes are Google's; no network is
 * involved, and the service account key is generated in the test rather than borrowed from
 * anywhere real.
 *
 * <p>What matters most here is not that a crawl produces content. It is what the permission
 * mapping refuses to grant, and when the crawl is willing to call itself complete — because that
 * claim is what lets the ledger retire everything the crawl left out.
 */
class GoogleDriveConnectorBatchSourceTests {

    private static final UUID ORG = UUID.fromString("bb000000-0000-4000-8000-000000000001");
    private static final UUID SPACE = UUID.fromString("bb000000-0000-4000-8000-000000000002");
    private static final UUID ACTOR = UUID.fromString("bb000000-0000-4000-8000-000000000003");
    private static final String CONNECTION = "example.com";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private ConnectorConnectionDirectory connections;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        setUpServerOnly();
        connections = mock(ConnectorConnectionDirectory.class);
        when(connections.resolveCredential(any(), any(), any()))
                .thenReturn(Optional.of(SecretValue.of(GoogleDriveTestKeys.serviceAccountKeyJson())));
    }

    /**
     * A second pass needs a second server: MockRestServiceServer refuses expectations added
     * after a request has been made, so rebinding is how a test spans two polls.
     */
    private void setUpServerOnly() {
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
    }

    @Test
    void turnsADriveIntoTheCrawlContract() {
        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "The deploy window is Thursday.");

        ConnectorCrawlBatch batch = crawl(List.of());

        assertEquals("google_drive", batch.sourceSystem());
        assertEquals(CONNECTION, batch.sourceConnectionKey());
        assertEquals(ORG, batch.organizationId());

        var document = batch.contents().getFirst();
        assertEquals("1-handbook", document.externalObjectId(), "a file is keyed by Drive's own id");
        assertEquals("Engineering handbook", document.title());
        assertTrue(document.body().contains("The deploy window is Thursday"));
    }

    /**
     * The mapping's refusals, which are the part worth pinning. A public link must not become an
     * internal grant, and a permission type this adapter does not understand must not be guessed.
     */
    @Test
    void grantsUsersGroupsAndDomainsButNeverAPublicLink() {
        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "Anything.");

        ConnectorCrawlBatch batch = crawl(List.of());

        List<String> granted = batch.permissions().getFirst().grants().stream()
                .map(grant -> grant.principalExternalKey())
                .toList();
        assertEquals(
                List.of("mai@example.com", "eng@example.com", "domain:example.com"),
                granted,
                "a user, a group and a domain all grant; the anyone-with-the-link permission does not");
    }

    /**
     * A domain grant resolves through the users this crawl actually saw. Drive cannot enumerate a
     * domain, so the alternative to under-granting would be inventing members.
     */
    @Test
    void aDomainGroupIsMadeOfTheUsersTheCrawlActuallySaw() {
        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "Anything.");

        ConnectorCrawlBatch batch = crawl(List.of());

        ConnectorIdentityItem domainGroup = batch.identities().stream()
                .filter(identity -> "domain:example.com".equals(identity.externalKey()))
                .findFirst()
                .orElseThrow();
        assertEquals(SourcePrincipalKind.SOURCE_GROUP, domainGroup.kind());
        assertEquals(List.of("owner@example.com", "mai@example.com"), domainGroup.memberExternalKeys());
        assertFalse(
                domainGroup.memberExternalKeys().contains("eng@example.com"),
                "a group address is not a person, so it is not a member of the domain group");
    }

    @Test
    void observesOwnersAndSharedUsersAsVerifiedPeople() {
        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "Anything.");

        ConnectorCrawlBatch batch = crawl(List.of());

        List<ConnectorIdentityItem> users = batch.identities().stream()
                .filter(identity -> identity.kind() == SourcePrincipalKind.SOURCE_USER)
                .toList();
        assertEquals(
                List.of("owner@example.com", "mai@example.com"),
                users.stream().map(ConnectorIdentityItem::externalKey).toList(),
                "the owner counts even when nothing was shared with anybody");
        assertTrue(
                users.getFirst().ssoVerified(),
                "Google confirms address ownership before an account exists, so it vouches");
    }

    @Test
    void claimsCompletenessOnlyForAnUnfilteredUninterruptedCrawl() {
        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "Anything.");

        assertTrue(crawl(List.of()).crawlComplete(), "nothing was filtered and nothing was skipped");
    }

    @Test
    void withdrawsTheCompletenessClaimWhenOnlySomeFoldersWereAskedFor() {
        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "Anything.");

        // A crawl of two folders says nothing about the rest of the Drive, and the ledger must
        // not read its silence as a deletion.
        assertFalse(crawl(List.of("1AbC")).crawlComplete());
    }

    @Test
    void withdrawsTheCompletenessClaimWhenAFileCouldNotBeRead() {
        expectToken();
        expectList(TWO_READABLE_FILES);
        expectExport("1-handbook", "Anything.");
        expectExport("3-charter", "Anything.");
        server.expect(ExpectedCount.once(), requestTo(Matchers.containsString("/files/2-runbook/export")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"errors\":[{\"reason\":\"insufficientPermissions\"}]}}"));

        ConnectorCrawlBatch batch = crawl(List.of());

        assertFalse(batch.crawlComplete(), "one unreadable file is indistinguishable from a deletion");
        assertEquals(2, batch.contents().size(), "the files that could be read still were");
    }

    /**
     * Between content crawls no document body is fetched at all. Drive makes this cheaper than
     * Slack does: one listing already carries every file's sharing.
     */
    @Test
    void readsNoDocumentBodiesBetweenContentCrawls() {
        MutableClock clock = new MutableClock(java.time.Instant.parse("2026-07-23T09:00:00Z"));
        GoogleDriveConnectorBatchSource source = source(clock);

        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "Anything.");
        source.pendingBatches();

        // No export or download is expected for the second pass; MockRestServiceServer fails the
        // request if one is made, which is the assertion.
        setUpServerOnly();
        expectToken();
        expectList(FILES);
        clock.advance(Duration.ofMinutes(5));
        ConnectorCrawlBatch permissionsOnly = source.pendingBatches().batches().getFirst();

        assertTrue(permissionsOnly.contents().isEmpty());
        assertFalse(permissionsOnly.permissions().isEmpty(), "access is still re-read");
        assertFalse(
                permissionsOnly.crawlComplete(),
                "a pass that opened no document cannot authorize retiring one");
        server.verify();
    }

    @Test
    void reissuesAContentCrawlOnceTheIntervalElapses() {
        MutableClock clock = new MutableClock(java.time.Instant.parse("2026-07-23T09:00:00Z"));
        GoogleDriveConnectorBatchSource source = source(clock);

        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "First.");
        source.pendingBatches();

        setUpServerOnly();
        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "Second, edited.");
        clock.advance(Duration.ofMinutes(61));
        ConnectorCrawlBatch again = source.pendingBatches().batches().getFirst();

        assertFalse(again.contents().isEmpty(), "content is re-read once its interval has elapsed");
    }

    @Test
    void anEditedDocumentGetsANewContentRevisionAndAnUnchangedOneDoesNot() {
        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "The deploy window is Thursday.");
        String first = crawl(List.of()).contents().getFirst().contentRevision();

        setUp();
        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "The deploy window is Thursday.");
        String unchanged = crawl(List.of()).contents().getFirst().contentRevision();

        setUp();
        expectToken();
        expectList(FILES);
        expectExport("1-handbook", "The deploy window moved to Friday.");
        String edited = crawl(List.of()).contents().getFirst().contentRevision();

        assertEquals(first, unchanged, "the revision is the text, so a re-crawl of the same text is free");
        assertNotEquals(first, edited, "and an edit is a new revision");
    }

    @Test
    void reportsAConnectionWithNoStoredCredentialRatherThanSkippingItSilently() {
        when(connections.resolveCredential(any(), any(), any())).thenReturn(Optional.empty());
        when(connections.enabledCrawls("google_drive")).thenReturn(List.of(configuration(List.of())));

        ConnectorPoll poll = source(null).pendingBatches();

        assertTrue(poll.batches().isEmpty());
        assertEquals(1, poll.unavailable().size(), "the connection that produced nothing is still an attempt");
        assertEquals("no_credential", poll.unavailable().getFirst().errorCode());
        server.verify();
    }

    @Test
    void reportsACredentialThatIsNotAServiceAccountKey() {
        when(connections.resolveCredential(any(), any(), any()))
                .thenReturn(Optional.of(SecretValue.of("{\"type\":\"authorized_user\"}")));
        when(connections.enabledCrawls("google_drive")).thenReturn(List.of(configuration(List.of())));

        ConnectorPoll poll = source(null).pendingBatches();

        assertEquals("invalid_key", poll.unavailable().getFirst().errorCode());
        assertFalse(
                poll.unavailable().getFirst().message().contains("private_key"),
                "a refusal describes the credential, it does not repeat any of it");
        server.verify();
    }

    // --- harness -------------------------------------------------------------------------

    private ConnectorCrawlBatch crawl(List<String> folderIds) {
        when(connections.enabledCrawls("google_drive")).thenReturn(List.of(configuration(folderIds)));
        return source(null).pendingBatches().batches().getFirst();
    }

    private GoogleDriveConnectorBatchSource source(MutableClock clock) {
        if (clock != null) {
            when(connections.enabledCrawls("google_drive")).thenReturn(List.of(configuration(List.of())));
        }
        return new GoogleDriveConnectorBatchSource(
                connections,
                builder,
                new tools.jackson.databind.ObjectMapper(),
                clock == null ? java.time.Clock.systemUTC() : clock);
    }

    private static ConnectorCrawlConfiguration configuration(List<String> folderIds) {
        String folders = folderIds.stream()
                .map(id -> "\"" + id + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return new ConnectorCrawlConfiguration(
                ORG,
                "google_drive",
                CONNECTION,
                SPACE,
                ACTOR,
                "{\"folderIds\":[" + folders + "],\"maxFiles\":500}",
                Duration.ofMinutes(60));
    }

    private void expectToken() {
        server.expect(ExpectedCount.manyTimes(), requestTo(Matchers.containsString("oauth2.googleapis.com/token")))
                .andRespond(withSuccess(
                        "{\"access_token\":\"ya29.not-a-real-token\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
    }

    private void expectList(String body) {
        server.expect(ExpectedCount.once(), requestTo(Matchers.containsString("/drive/v3/files?")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectExport(String fileId, String text) {
        server.expect(ExpectedCount.once(), requestTo(Matchers.containsString("/files/" + fileId + "/export")))
                .andRespond(withSuccess(text, MediaType.TEXT_PLAIN));
    }

    /**
     * One Google Doc, owned by one person and shared four ways: a user, a group, the whole
     * domain, and a public link.
     */
    private static final String FILES = """
            {"files":[{
              "id":"1-handbook",
              "name":"Engineering handbook",
              "mimeType":"application/vnd.google-apps.document",
              "modifiedTime":"2026-07-20T10:00:00Z",
              "trashed":false,
              "owners":[{"emailAddress":"owner@example.com","displayName":"Owner"}],
              "permissions":[
                {"id":"p1","type":"user","emailAddress":"mai@example.com","role":"reader"},
                {"id":"p2","type":"group","emailAddress":"eng@example.com","role":"reader"},
                {"id":"p3","type":"domain","domain":"example.com","role":"reader"},
                {"id":"p4","type":"anyone","role":"reader"}
              ]
            }]}
            """;

    private static final String TWO_READABLE_FILES = """
            {"files":[
              {"id":"1-handbook","name":"Engineering handbook",
               "mimeType":"application/vnd.google-apps.document","trashed":false,
               "owners":[{"emailAddress":"owner@example.com","displayName":"Owner"}],
               "permissions":[{"id":"p1","type":"user","emailAddress":"mai@example.com","role":"reader"}]},
              {"id":"2-runbook","name":"Runbook",
               "mimeType":"application/vnd.google-apps.document","trashed":false,
               "owners":[{"emailAddress":"owner@example.com","displayName":"Owner"}],
               "permissions":[{"id":"p2","type":"user","emailAddress":"mai@example.com","role":"reader"}]},
              {"id":"3-charter","name":"Team charter",
               "mimeType":"application/vnd.google-apps.document","trashed":false,
               "owners":[{"emailAddress":"owner@example.com","displayName":"Owner"}],
               "permissions":[{"id":"p3","type":"user","emailAddress":"mai@example.com","role":"reader"}]},
              {"id":"4-image","name":"Diagram","mimeType":"image/png","trashed":false,
               "owners":[{"emailAddress":"owner@example.com","displayName":"Owner"}],
               "permissions":[]}
            ]}
            """;

    /** A clock the test moves, so the content interval is exercised without waiting for it. */
    private static final class MutableClock extends java.time.Clock {

        private java.time.Instant now;

        private MutableClock(java.time.Instant now) {
            this.now = now;
        }

        private void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return java.time.ZoneOffset.UTC;
        }

        @Override
        public java.time.Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public java.time.Instant instant() {
            return now;
        }
    }
}
