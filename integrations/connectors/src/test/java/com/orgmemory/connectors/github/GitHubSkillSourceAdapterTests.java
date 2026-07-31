package com.orgmemory.connectors.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.orgmemory.core.assetregistry.SkillGitHubSourcePort;
import com.orgmemory.core.assetregistry.SkillPackageSpec;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionConfiguration;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionDirectory;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.BusinessUnavailableException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import com.orgmemory.core.shared.secret.SecretValue;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class GitHubSkillSourceAdapterTests {

    private static final UUID ORGANIZATION =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID ACTOR =
            UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String SHA = "a".repeat(40);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void importsAPublicRepositoryWithoutResolvingAStoredCredential() throws Exception {
        RestClient.Builder apiBuilder = RestClient.builder();
        MockRestServiceServer api = MockRestServiceServer.bindTo(apiBuilder).build();
        RestClient.Builder downloadBuilder = RestClient.builder();
        MockRestServiceServer download = MockRestServiceServer.bindTo(downloadBuilder).build();
        api.expect(requestTo("https://api.github.com/repos/acme/skills"))
                .andRespond(withSuccess("{\"id\":77,\"private\":false}", MediaType.APPLICATION_JSON));
        api.expect(requestTo("https://api.github.com/repos/acme/skills/commits/main"))
                .andRespond(withSuccess("{\"sha\":\"" + SHA + "\"}", MediaType.APPLICATION_JSON));
        download.expect(requestTo("https://codeload.github.com/acme/skills/tar.gz/" + SHA))
                .andRespond(withSuccess(repositoryArchive(), MediaType.APPLICATION_OCTET_STREAM));
        ConnectorConnectionDirectory connections = mock(ConnectorConnectionDirectory.class);
        GitHubSkillSourceAdapter adapter = new GitHubSkillSourceAdapter(
                connections,
                mock(PermissionAuditService.class),
                apiBuilder.build(),
                downloadBuilder.build(),
                RestClient.builder(),
                new ObjectMapper(),
                CLOCK);

        SkillGitHubSourcePort.FetchResult result = adapter.fetch(request("main", ""));

        assertEquals(SkillPackageSpec.Visibility.PUBLIC, result.visibility());
        assertEquals(SHA, result.revision());
        assertEquals("skills/triage/SKILL.md", result.packages().getFirst().path());
        verify(connections, org.mockito.Mockito.never()).resolveCredential(any(), any(), any());
        api.verify();
        download.verify();
    }

    @Test
    void auditsPrivateCredentialUseAndStripsAuthorizationFromCodeload() throws Exception {
        RestClient.Builder anonymousBuilder = RestClient.builder();
        MockRestServiceServer anonymous = MockRestServiceServer.bindTo(anonymousBuilder).build();
        RestClient.Builder authenticatedBuilder = RestClient.builder();
        MockRestServiceServer authenticated =
                MockRestServiceServer.bindTo(authenticatedBuilder).build();
        RestClient.Builder downloadBuilder = RestClient.builder();
        MockRestServiceServer download = MockRestServiceServer.bindTo(downloadBuilder).build();
        anonymous.expect(requestTo("https://api.github.com/repos/acme/skills"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        authenticated.expect(requestTo(
                        "https://api.github.com/app/installations/67890/access_tokens"))
                .andRespond(withSuccess(
                        "{\"token\":\"ghs_test\",\"expires_at\":\"2026-07-28T11:00:00Z\"}",
                        MediaType.APPLICATION_JSON));
        authenticated.expect(requestTo("https://api.github.com/repos/acme/skills"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghs_test"))
                .andRespond(withSuccess("{\"id\":77,\"private\":true}", MediaType.APPLICATION_JSON));
        authenticated.expect(requestTo("https://api.github.com/repos/acme/skills/commits/" + SHA))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghs_test"))
                .andRespond(withSuccess("{\"sha\":\"" + SHA + "\"}", MediaType.APPLICATION_JSON));
        authenticated.expect(requestTo("https://api.github.com/repos/acme/skills/tarball/" + SHA))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ghs_test"))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION,
                                "https://codeload.github.com/acme/skills/legacy.tar.gz/" + SHA));
        download.expect(requestTo(Matchers.startsWith("https://codeload.github.com/")))
                .andExpect(request -> assertFalse(
                        request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)))
                .andRespond(withSuccess(repositoryArchive(), MediaType.APPLICATION_OCTET_STREAM));
        ConnectorConnectionDirectory connections = mock(ConnectorConnectionDirectory.class);
        when(connections.configuration(ORGANIZATION, "github", "private-app"))
                .thenReturn(Optional.of(new ConnectorConnectionConfiguration(
                        "github",
                        "private-app",
                        "{\"allowPrivateSkillImports\":true,\"repositoryIds\":[\"77\"]}",
                        true)));
        when(connections.resolveCredential(ORGANIZATION, "github", "private-app"))
                .thenReturn(Optional.of(SecretValue.of(GitHubTestCredential.json())));
        PermissionAuditService audit = mock(PermissionAuditService.class);
        GitHubSkillSourceAdapter adapter = new GitHubSkillSourceAdapter(
                connections,
                audit,
                anonymousBuilder.build(),
                downloadBuilder.build(),
                authenticatedBuilder,
                new ObjectMapper(),
                CLOCK);

        SkillGitHubSourcePort.FetchResult result = adapter.fetch(request(SHA, "private-app"));

        assertEquals(SkillPackageSpec.Visibility.PRIVATE, result.visibility());
        ArgumentCaptor<PermissionAuditCommand> command =
                ArgumentCaptor.forClass(PermissionAuditCommand.class);
        verify(audit).record(command.capture());
        assertEquals("SKILL_GITHUB_IMPORT_CREDENTIAL_USE", command.getValue().operation());
        assertEquals("private-app", command.getValue().resourceId());
        anonymous.verify();
        authenticated.verify();
        download.verify();
    }

    @Test
    void doesNotTreatAnAnonymousRateLimitAsEvidenceOfAPrivateRepository() {
        RestClient.Builder anonymousBuilder = RestClient.builder();
        MockRestServiceServer anonymous = MockRestServiceServer.bindTo(anonymousBuilder).build();
        anonymous.expect(requestTo("https://api.github.com/repos/acme/skills"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .header("X-RateLimit-Remaining", "0"));
        ConnectorConnectionDirectory connections = mock(ConnectorConnectionDirectory.class);
        GitHubSkillSourceAdapter adapter = new GitHubSkillSourceAdapter(
                connections,
                mock(PermissionAuditService.class),
                anonymousBuilder.build(),
                RestClient.builder().build(),
                RestClient.builder(),
                new ObjectMapper(),
                CLOCK);

        BusinessUnavailableException failure = assertThrows(
                BusinessUnavailableException.class,
                () -> adapter.fetch(request("main", "private-app")));

        assertEquals("skill.github-rate-limited", failure.code());
        verify(connections, never()).configuration(any(), any(), any());
        verify(connections, never()).resolveCredential(any(), any(), any());
        anonymous.verify();
    }

    @Test
    void privateImportSettingsFailClosedWithoutApprovedRepositoryIds() {
        GitHubSkillImportSettings missing = GitHubSkillImportSettings.from(
                "{\"allowPrivateSkillImports\":true}");
        GitHubSkillImportSettings empty = GitHubSkillImportSettings.from(
                "{\"allowPrivateSkillImports\":true,\"repositoryIds\":[]}");

        assertFalse(missing.valid());
        assertFalse(empty.valid());
        assertFalse(missing.allowsRepository("77"));
        assertFalse(empty.allowsRepository(""));
    }

    @Test
    void refusesPrivateImportWhenTheConnectionPolicyDisablesIt() {
        RestClient.Builder anonymousBuilder = RestClient.builder();
        MockRestServiceServer anonymous = MockRestServiceServer.bindTo(anonymousBuilder).build();
        anonymous.expect(requestTo("https://api.github.com/repos/acme/skills"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        ConnectorConnectionDirectory connections = mock(ConnectorConnectionDirectory.class);
        when(connections.configuration(ORGANIZATION, "github", "private-app"))
                .thenReturn(Optional.of(new ConnectorConnectionConfiguration(
                        "github",
                        "private-app",
                        "{\"allowPrivateSkillImports\":false,\"repositoryIds\":[\"77\"]}",
                        true)));
        GitHubSkillSourceAdapter adapter = new GitHubSkillSourceAdapter(
                connections,
                mock(PermissionAuditService.class),
                anonymousBuilder.build(),
                RestClient.builder().build(),
                RestClient.builder(),
                new ObjectMapper(),
                CLOCK);

        BusinessValidationException failure = assertThrows(
                BusinessValidationException.class,
                () -> adapter.fetch(request(SHA, "private-app")));

        assertEquals("skill.github-private-import-disabled", failure.code());
        verify(connections, never()).resolveCredential(any(), any(), any());
        anonymous.verify();
    }

    @Test
    void refusesAnArchiveResponseOverTwentyFiveMebibytes() {
        RestClient.Builder apiBuilder = RestClient.builder();
        MockRestServiceServer api = MockRestServiceServer.bindTo(apiBuilder).build();
        RestClient.Builder downloadBuilder = RestClient.builder();
        MockRestServiceServer download = MockRestServiceServer.bindTo(downloadBuilder).build();
        api.expect(requestTo("https://api.github.com/repos/acme/skills"))
                .andRespond(withSuccess("{\"id\":77,\"private\":false}", MediaType.APPLICATION_JSON));
        api.expect(requestTo("https://api.github.com/repos/acme/skills/commits/" + SHA))
                .andRespond(withSuccess("{\"sha\":\"" + SHA + "\"}", MediaType.APPLICATION_JSON));
        download.expect(requestTo("https://codeload.github.com/acme/skills/tar.gz/" + SHA))
                .andRespond(withSuccess(new byte[25 * 1024 * 1024 + 1], MediaType.APPLICATION_OCTET_STREAM));
        GitHubSkillSourceAdapter adapter = new GitHubSkillSourceAdapter(
                mock(ConnectorConnectionDirectory.class),
                mock(PermissionAuditService.class),
                apiBuilder.build(),
                downloadBuilder.build(),
                RestClient.builder(),
                new ObjectMapper(),
                CLOCK);

        BusinessValidationException failure = assertThrows(
                BusinessValidationException.class,
                () -> adapter.fetch(request(SHA, "")));

        assertEquals("skill.github-archive-too-large", failure.code());
        api.verify();
        download.verify();
    }

    @Test
    void refusesAPrivateArchiveRedirectOutsideCodeload() {
        PrivateFixture fixture = privateFixture("77");
        fixture.authenticated().expect(requestTo(
                        "https://api.github.com/repos/acme/skills/commits/" + SHA))
                .andRespond(withSuccess("{\"sha\":\"" + SHA + "\"}", MediaType.APPLICATION_JSON));
        fixture.authenticated().expect(requestTo(
                        "https://api.github.com/repos/acme/skills/tarball/" + SHA))
                .andRespond(withStatus(HttpStatus.FOUND)
                        .header(HttpHeaders.LOCATION, "https://example.test/archive.tar.gz"));

        BusinessUnavailableException failure = assertThrows(
                BusinessUnavailableException.class,
                () -> fixture.adapter().fetch(request(SHA, "private-app")));

        assertEquals("skill.github-redirect-invalid", failure.code());
        fixture.verify();
    }

    @Test
    void refusesAPrivateArchiveRedirectWithoutLocation() {
        PrivateFixture fixture = privateFixture("77");
        fixture.authenticated().expect(requestTo(
                        "https://api.github.com/repos/acme/skills/commits/" + SHA))
                .andRespond(withSuccess("{\"sha\":\"" + SHA + "\"}", MediaType.APPLICATION_JSON));
        fixture.authenticated().expect(requestTo(
                        "https://api.github.com/repos/acme/skills/tarball/" + SHA))
                .andRespond(withStatus(HttpStatus.FOUND));

        BusinessUnavailableException failure = assertThrows(
                BusinessUnavailableException.class,
                () -> fixture.adapter().fetch(request(SHA, "private-app")));

        assertEquals("skill.github-redirect-invalid", failure.code());
        fixture.verify();
    }

    @Test
    void auditsDeniedRepositorySelectionWithoutRecordingAllow() {
        PermissionAuditService audit = mock(PermissionAuditService.class);
        PrivateFixture fixture = privateFixture("88", audit);

        assertThrows(
                com.orgmemory.core.shared.error.BusinessNotFoundException.class,
                () -> fixture.adapter().fetch(request(SHA, "private-app")));

        ArgumentCaptor<PermissionAuditCommand> command =
                ArgumentCaptor.forClass(PermissionAuditCommand.class);
        verify(audit).record(command.capture());
        assertEquals(PermissionAuditDecision.DENY, command.getValue().decision());
        assertEquals("REPOSITORY_NOT_APPROVED", command.getValue().reasonCode());
        fixture.verify();
    }

    private static SkillGitHubSourcePort.FetchRequest request(
            String revision, String connectionKey) {
        return new SkillGitHubSourcePort.FetchRequest(
                ORGANIZATION,
                ACTOR,
                "https://github.com/acme/skills.git",
                revision,
                "skills",
                connectionKey);
    }

    private static PrivateFixture privateFixture(String repositoryId) {
        return privateFixture(repositoryId, mock(PermissionAuditService.class));
    }

    private static PrivateFixture privateFixture(
            String repositoryId, PermissionAuditService audit) {
        RestClient.Builder anonymousBuilder = RestClient.builder();
        MockRestServiceServer anonymous = MockRestServiceServer.bindTo(anonymousBuilder).build();
        RestClient.Builder authenticatedBuilder = RestClient.builder();
        MockRestServiceServer authenticated =
                MockRestServiceServer.bindTo(authenticatedBuilder).build();
        RestClient.Builder downloadBuilder = RestClient.builder();
        MockRestServiceServer download = MockRestServiceServer.bindTo(downloadBuilder).build();
        anonymous.expect(requestTo("https://api.github.com/repos/acme/skills"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));
        authenticated.expect(requestTo(
                        "https://api.github.com/app/installations/67890/access_tokens"))
                .andRespond(withSuccess(
                        "{\"token\":\"ghs_test\",\"expires_at\":\"2026-07-28T11:00:00Z\"}",
                        MediaType.APPLICATION_JSON));
        authenticated.expect(requestTo("https://api.github.com/repos/acme/skills"))
                .andRespond(withSuccess(
                        "{\"id\":" + repositoryId + ",\"private\":true}",
                        MediaType.APPLICATION_JSON));
        ConnectorConnectionDirectory connections = mock(ConnectorConnectionDirectory.class);
        when(connections.configuration(ORGANIZATION, "github", "private-app"))
                .thenReturn(Optional.of(new ConnectorConnectionConfiguration(
                        "github",
                        "private-app",
                        "{\"allowPrivateSkillImports\":true,\"repositoryIds\":[\"77\"]}",
                        true)));
        when(connections.resolveCredential(ORGANIZATION, "github", "private-app"))
                .thenReturn(Optional.of(SecretValue.of(GitHubTestCredential.json())));
        GitHubSkillSourceAdapter adapter = new GitHubSkillSourceAdapter(
                connections,
                audit,
                anonymousBuilder.build(),
                downloadBuilder.build(),
                authenticatedBuilder,
                new ObjectMapper(),
                CLOCK);
        return new PrivateFixture(adapter, anonymous, authenticated, download);
    }

    private record PrivateFixture(
            GitHubSkillSourceAdapter adapter,
            MockRestServiceServer anonymous,
            MockRestServiceServer authenticated,
            MockRestServiceServer download) {

        void verify() {
            anonymous.verify();
            authenticated.verify();
            download.verify();
        }
    }

    private static byte[] repositoryArchive() throws Exception {
        byte[] manifest = """
                ---
                name: triage
                description: Triage support requests.
                ---
                # Triage
                """.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (var gzip = new GzipCompressorOutputStream(bytes);
                var tar = new TarArchiveOutputStream(gzip)) {
            TarArchiveEntry entry = new TarArchiveEntry("skills-" + SHA + "/skills/triage/SKILL.md");
            entry.setSize(manifest.length);
            tar.putArchiveEntry(entry);
            tar.write(manifest);
            tar.closeArchiveEntry();
        }
        return bytes.toByteArray();
    }
}
