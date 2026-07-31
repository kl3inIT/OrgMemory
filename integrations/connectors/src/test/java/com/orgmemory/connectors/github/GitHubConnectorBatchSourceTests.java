package com.orgmemory.connectors.github;

import com.orgmemory.core.knowledge.acl.SourcePrincipalKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.orgmemory.core.knowledge.ConnectorCaptureStatus;
import com.orgmemory.core.knowledge.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorCrawlConfiguration;
import com.orgmemory.core.knowledge.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.ConnectorObjectDirectory;
import com.orgmemory.core.knowledge.ConnectorPoll;
import com.orgmemory.core.knowledge.ConnectorSyncComponent;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GitHubConnectorBatchSourceTests {

    private static final UUID ORG = UUID.fromString("bb000000-0000-4000-8000-000000000001");
    private static final UUID SPACE = UUID.fromString("bb000000-0000-4000-8000-000000000002");
    private static final UUID ACTOR = UUID.fromString("bb000000-0000-4000-8000-000000000003");
    private static final String CONNECTION = "42";
    private static final String OBJECT_ID = "77__201";
    private static final Instant NOW = Instant.parse("2026-07-28T10:00:00Z");

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private ConnectorConnectionDirectory connections;
    private ConnectorObjectDirectory objects;
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        connections = mock(ConnectorConnectionDirectory.class);
        objects = mock(ConnectorObjectDirectory.class);
        clock = new MutableClock(NOW);
        when(connections.resolveCredential(any(), any(), any()))
                .thenReturn(Optional.of(SecretValue.of(GitHubTestCredential.json())));
        when(connections.enabledCrawls("github")).thenReturn(List.of(configuration("{}")));
        when(objects.activeObjectIds(any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void mapsARepositoryAudienceAndWorkItemToStableNativeIds() {
        expectInstallation(1);
        expectToken();
        expectRepositories(PRIVATE_REPOSITORY);
        expectCollaborators(TWO_READERS);
        expectIssues(ONE_ISSUE);

        ConnectorCrawlBatch batch = source().pendingBatches().batches().getFirst();

        assertEquals("github", batch.sourceSystem());
        assertEquals(CONNECTION, batch.sourceConnectionKey());
        assertEquals(OBJECT_ID, batch.contents().getFirst().externalObjectId());
        assertTrue(batch.contents().getFirst().title().contains("acme/platform #9"));
        assertTrue(batch.contents().getFirst().body().contains("Roll out the authorization ledger"));
        assertEquals(
                "repository:77:readers",
                batch.permissions().getFirst().grants().getFirst().principalNativeId());
        assertEquals(
                List.of("101", "102"),
                batch.memberships().getFirst().members().stream()
                        .map(member -> member.nativePrincipalId())
                        .toList());

        List<ConnectorIdentityItem> users = batch.identities().stream()
                .filter(identity -> identity.kind() == SourcePrincipalKind.SOURCE_USER)
                .toList();
        assertEquals(List.of("101", "102"), users.stream()
                .map(ConnectorIdentityItem::nativePrincipalId)
                .toList());
        assertTrue(users.stream().allMatch(user -> user.email() == null && !user.ssoVerified()));
        assertTrue(batch.crawlComplete());
        server.verify();
    }

    @Test
    void teamDerivedReaderRemovalChangesOnlyMembershipOnTheNextPoll() {
        when(objects.activeObjectIds(ORG, "github", CONNECTION)).thenReturn(List.of(OBJECT_ID));
        GitHubConnectorBatchSource source = source();

        expectInstallation(2);
        expectToken();
        expectRepositories(PRIVATE_REPOSITORY);
        expectCollaborators(TWO_READERS);
        expectIssues(ONE_ISSUE);

        expectRepositories(PRIVATE_REPOSITORY);
        expectCollaborators(ONE_READER);

        ConnectorCrawlBatch initial = source.pendingBatches().batches().getFirst();
        clock.advance(Duration.ofMinutes(5));
        ConnectorCrawlBatch membershipOnly = source.pendingBatches().batches().getFirst();

        assertTrue(membershipOnly.contents().isEmpty(),
                "membership refreshes inside the content interval without recrawling content");
        assertEquals(
                initial.permissions().getFirst().grants(),
                membershipOnly.permissions().getFirst().grants());
        assertEquals(
                List.of("101"),
                membershipOnly.memberships().getFirst().members().stream()
                        .map(member -> member.nativePrincipalId())
                        .toList());
        assertNotEquals(
                initial.componentState(ConnectorSyncComponent.MEMBERSHIP).cursor(),
                membershipOnly.componentState(ConnectorSyncComponent.MEMBERSHIP).cursor());
        server.verify();
    }

    @Test
    void unreadableCollaboratorsAreIncompleteAndNeverBecomeAnEmptyAcl() {
        expectInstallation(1);
        expectToken();
        expectRepositories(PRIVATE_REPOSITORY);
        server.expect(requestTo(Matchers.containsString("/collaborators")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        ConnectorCrawlBatch batch = source().pendingBatches().batches().getFirst();

        assertEquals(
                ConnectorCaptureStatus.INCOMPLETE,
                batch.componentState(ConnectorSyncComponent.PERMISSION).captureStatus());
        assertEquals(
                ConnectorCaptureStatus.INCOMPLETE,
                batch.componentState(ConnectorSyncComponent.MEMBERSHIP).captureStatus());
        assertTrue(batch.permissions().isEmpty());
        assertTrue(batch.contents().isEmpty());
        assertEquals(
                ConnectorCaptureStatus.INCOMPLETE,
                batch.memberships().getFirst().captureStatus());
        server.verify();
    }

    @Test
    void configuredPublicRepositoryIsRejectedRatherThanGivenANarrowAcl() {
        when(connections.enabledCrawls("github"))
                .thenReturn(List.of(configuration("{\"repositoryIds\":[77]}")));
        expectInstallation(1);
        expectToken();
        expectRepositories(PUBLIC_REPOSITORY);

        ConnectorPoll poll = source().pendingBatches();

        assertTrue(poll.batches().isEmpty());
        assertEquals("repository_not_admissible", poll.unavailable().getFirst().errorCode());
        server.verify();
    }

    @Test
    void issueBoundMarksOnlyContentIncomplete() {
        when(connections.enabledCrawls("github"))
                .thenReturn(List.of(configuration("{\"maxItemsPerRepository\":1}")));
        expectInstallation(1);
        expectToken();
        expectRepositories(PRIVATE_REPOSITORY);
        expectCollaborators(ONE_READER);
        expectIssues(TWO_ISSUES);

        ConnectorCrawlBatch batch = source().pendingBatches().batches().getFirst();

        assertEquals(1, batch.contents().size());
        assertEquals(
                ConnectorCaptureStatus.INCOMPLETE,
                batch.componentState(ConnectorSyncComponent.CONTENT).captureStatus());
        assertEquals(
                ConnectorCaptureStatus.COMPLETE,
                batch.componentState(ConnectorSyncComponent.PERMISSION).captureStatus());
        assertEquals(
                ConnectorCaptureStatus.COMPLETE,
                batch.componentState(ConnectorSyncComponent.MEMBERSHIP).captureStatus());
        assertFalse(batch.crawlComplete());
        server.verify();
    }

    @Test
    void missingCredentialIsReportedAsConnectionActivity() {
        when(connections.resolveCredential(any(), any(), any())).thenReturn(Optional.empty());

        ConnectorPoll poll = source().pendingBatches();

        assertTrue(poll.batches().isEmpty());
        assertEquals("no_credential", poll.unavailable().getFirst().errorCode());
    }

    @Test
    void installationForAnotherOrganizationCannotRepointTheConnection() {
        expectInstallation("""
                {"id":67890,"account":{"id":99,"login":"other","type":"Organization"},
                 "app_slug":"orgmemory-test",
                 "permissions":{"metadata":"read","issues":"read"}}
                """);

        ConnectorPoll poll = source().pendingBatches();

        assertTrue(poll.batches().isEmpty());
        assertEquals("connection_mismatch", poll.unavailable().getFirst().errorCode());
        server.verify();
    }

    @Test
    void installationWithoutIssuesReadCannotProduceContent() {
        expectInstallation("""
                {"id":67890,"account":{"id":42,"login":"acme","type":"Organization"},
                 "app_slug":"orgmemory-test",
                 "permissions":{"metadata":"read"}}
                """);

        ConnectorPoll poll = source().pendingBatches();

        assertTrue(poll.batches().isEmpty());
        assertEquals("issues_read_required", poll.unavailable().getFirst().errorCode());
        server.verify();
    }

    @Test
    void malformedExplicitScopeNeverWidensToEveryRepository() {
        when(connections.enabledCrawls("github"))
                .thenReturn(List.of(configuration("{\"repositoryIds\":\"77\"}")));
        expectInstallation(1);

        ConnectorPoll poll = source().pendingBatches();

        assertTrue(poll.batches().isEmpty());
        assertEquals("invalid_source_config", poll.unavailable().getFirst().errorCode());
        server.verify();
    }

    private GitHubConnectorBatchSource source() {
        return new GitHubConnectorBatchSource(
                connections,
                objects,
                builder,
                new ObjectMapper(),
                clock);
    }

    private static ConnectorCrawlConfiguration configuration(String sourceConfig) {
        return new ConnectorCrawlConfiguration(
                ORG,
                "github",
                CONNECTION,
                SPACE,
                ACTOR,
                sourceConfig,
                Duration.ofHours(1),
                null);
    }

    private void expectInstallation(int count) {
        server.expect(
                        ExpectedCount.times(count),
                        requestTo("https://api.github.com/app/installations/67890"))
                .andRespond(withSuccess(validInstallation(), MediaType.APPLICATION_JSON));
    }

    private void expectInstallation(String body) {
        server.expect(requestTo("https://api.github.com/app/installations/67890"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String validInstallation() {
        return """
                {"id":67890,"account":{"id":42,"login":"acme","type":"Organization"},
                 "app_slug":"orgmemory-test",
                 "permissions":{"metadata":"read","issues":"read"}}
                """;
    }

    private void expectToken() {
        server.expect(requestTo("https://api.github.com/app/installations/67890/access_tokens"))
                .andRespond(withSuccess(
                        """
                        {"token":"ghs_not-a-real-token","expires_at":"2026-07-28T11:00:00Z",
                         "permissions":{"metadata":"read","issues":"read"}}
                        """,
                        MediaType.APPLICATION_JSON));
    }

    private void expectRepositories(String body) {
        server.expect(requestTo(Matchers.containsString("/installation/repositories")))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghs_not-a-real-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectCollaborators(String body) {
        server.expect(requestTo(Matchers.containsString("/repos/acme/platform/collaborators")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectIssues(String body) {
        server.expect(requestTo(Matchers.containsString("/repos/acme/platform/issues")))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static final String PRIVATE_REPOSITORY = """
            {"repositories":[{
              "id":77,
              "name":"platform",
              "full_name":"acme/platform",
              "private":true,
              "visibility":"private",
              "has_issues":true,
              "owner":{"id":42,"login":"acme","type":"Organization"}
            }]}
            """;

    private static final String PUBLIC_REPOSITORY = PRIVATE_REPOSITORY
            .replace("\"private\":true", "\"private\":false")
            .replace("\"visibility\":\"private\"", "\"visibility\":\"public\"");

    private static final String TWO_READERS = """
            [
              {"id":101,"login":"mai","type":"User","role_name":"read"},
              {"id":102,"login":"lan","type":"User","role_name":"read"}
            ]
            """;

    private static final String ONE_READER = """
            [{"id":101,"login":"mai","type":"User","role_name":"read"}]
            """;

    private static final String ONE_ISSUE = """
            [{
              "id":201,
              "number":9,
              "title":"Ship source authorization",
              "state":"open",
              "body":"Roll out the authorization ledger",
              "user":{"id":101,"login":"mai","type":"User"}
            }]
            """;

    private static final String TWO_ISSUES = """
            [
              {"id":201,"number":9,"title":"First","state":"open","body":"One",
               "user":{"id":101,"login":"mai","type":"User"}},
              {"id":202,"number":10,"title":"Second","state":"open","body":"Two",
               "user":{"id":101,"login":"mai","type":"User"}}
            ]
            """;

    private static final class MutableClock extends Clock {

        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
