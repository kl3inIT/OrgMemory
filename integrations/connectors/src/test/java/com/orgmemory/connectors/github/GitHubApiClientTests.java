package com.orgmemory.connectors.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class GitHubApiClientTests {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-07-28T10:00:00Z"), ZoneOffset.UTC);
    private static final String INSTALLATION_TOKEN = "ghs_not-a-real-token";

    private RestClient.Builder builder;
    private MockRestServiceServer server;
    private ObjectMapper objectMapper;
    private List<Duration> sleeps;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        objectMapper = new ObjectMapper();
        sleeps = new ArrayList<>();
    }

    @Test
    void signsTheDocumentedRs256JwtWithoutLeakingThePrivateKey() {
        GitHubAppKey key = GitHubAppKey.parse(GitHubTestCredential.json());
        GitHubInstallationTokenSource source =
                new GitHubInstallationTokenSource(builder, key, objectMapper, CLOCK);

        String jwt = source.appJwt();
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        JsonNode header = objectMapper.readTree(
                new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        JsonNode claims = objectMapper.readTree(
                new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8));

        assertEquals("RS256", header.path("alg").asString());
        assertEquals("12345", claims.path("iss").asString());
        assertEquals(
                CLOCK.instant().minusSeconds(60).getEpochSecond(),
                claims.path("iat").asLong());
        assertTrue(claims.path("exp").asLong() - claims.path("iat").asLong() <= 600);
        assertFalse(key.toString().contains("BEGIN PRIVATE KEY"));
    }

    @Test
    void followsLinkPaginationAndCachesTheInstallationToken() {
        expectToken();
        server.expect(requestTo(Matchers.containsString("/repos/acme/platform/collaborators")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + INSTALLATION_TOKEN))
                .andExpect(header("X-GitHub-Api-Version", "2026-03-10"))
                .andRespond(withSuccess(
                                "[{\"id\":101,\"login\":\"mai\",\"type\":\"User\"}]",
                                MediaType.APPLICATION_JSON)
                        .header(
                                HttpHeaders.LINK,
                                "<https://api.github.com/repositories/77/collaborators?page=2&per_page=100>; rel=\"next\""));
        server.expect(requestTo("https://api.github.com/repositories/77/collaborators?page=2&per_page=100"))
                .andRespond(withSuccess(
                        "[{\"id\":102,\"login\":\"lan\",\"type\":\"User\"}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(Matchers.containsString("/installation/repositories")))
                .andRespond(withSuccess(
                        "{\"repositories\":[{\"id\":77,\"name\":\"platform\"}]}",
                        MediaType.APPLICATION_JSON));

        GitHubApiClient client = client();
        List<JsonNode> collaborators = client.collaborators("acme", "platform");
        List<JsonNode> repositories = client.repositories();

        assertEquals(
                List.of("101", "102"),
                collaborators.stream().map(node -> node.path("id").asString()).toList());
        assertEquals("77", repositories.getFirst().path("id").asString());
        server.verify();
    }

    @Test
    void waitsOutARateLimitAndRetries() {
        expectToken();
        server.expect(requestTo(Matchers.containsString("/installation/repositories")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "7"));
        server.expect(requestTo(Matchers.containsString("/installation/repositories")))
                .andRespond(withSuccess("{\"repositories\":[]}", MediaType.APPLICATION_JSON));

        assertTrue(client().repositories().isEmpty());
        assertEquals(List.of(Duration.ofSeconds(7)), sleeps);
        server.verify();
    }

    @Test
    void doesNotRetryAPlainPermissionRefusal() {
        expectToken();
        server.expect(requestTo(Matchers.containsString("/repos/acme/platform/collaborators")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        GitHubApiException refused =
                assertThrows(GitHubApiException.class, () -> client().collaborators("acme", "platform"));

        assertEquals("github_http_403", refused.errorCode());
        assertTrue(sleeps.isEmpty());
        server.verify();
    }

    @Test
    void neverForwardsAnInstallationTokenToAPaginationHostGitHubDoesNotOwn() {
        expectToken();
        server.expect(requestTo(Matchers.containsString("/repos/acme/platform/collaborators")))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON)
                        .header(
                                HttpHeaders.LINK,
                                "<https://attacker.example/collaborators?page=2>; rel=\"next\""));

        GitHubApiException refused = assertThrows(
                GitHubApiException.class,
                () -> client().collaborators("acme", "platform"));

        assertEquals("unreadable_response", refused.errorCode());
        server.verify();
    }

    private GitHubApiClient client() {
        GitHubAppKey key = GitHubAppKey.parse(GitHubTestCredential.json());
        GitHubInstallationTokenSource tokens =
                new GitHubInstallationTokenSource(builder, key, objectMapper, CLOCK);
        return new GitHubApiClient(builder, tokens, objectMapper, sleeps::add);
    }

    private void expectToken() {
        server.expect(requestTo(
                        "https://api.github.com/app/installations/67890/access_tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(
                        HttpHeaders.AUTHORIZATION,
                        Matchers.matchesPattern("Bearer [^.]+\\.[^.]+\\.[^.]+")))
                .andRespond(withSuccess(
                        """
                        {"token":"ghs_not-a-real-token","expires_at":"2026-07-28T11:00:00Z",
                         "permissions":{"metadata":"read","issues":"read"}}
                        """,
                        MediaType.APPLICATION_JSON));
    }
}
