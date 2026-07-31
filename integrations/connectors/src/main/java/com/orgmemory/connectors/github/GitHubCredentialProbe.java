package com.orgmemory.connectors.github;

import com.orgmemory.core.knowledge.connector.ConnectorCredentialProbe;
import com.orgmemory.core.knowledge.connector.ConnectorCredentialProbeResult;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Verifies a GitHub App installation before its private key is stored. */
public class GitHubCredentialProbe implements ConnectorCredentialProbe {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GitHubCredentialProbe(RestClient.Builder restClientBuilder) {
        this(restClientBuilder, new ObjectMapper(), Clock.systemUTC());
    }

    GitHubCredentialProbe(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Clock clock) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String sourceSystem() {
        return GitHubSourceProfile.SOURCE_SYSTEM;
    }

    @Override
    public ConnectorCredentialProbeResult probe(SecretValue credential) {
        Objects.requireNonNull(credential, "credential");
        JsonNode installation;
        GitHubApiClient client;
        try {
            GitHubAppKey key = GitHubAppKey.parse(credential.expose());
            GitHubInstallationTokenSource tokens =
                    new GitHubInstallationTokenSource(restClientBuilder, key, objectMapper, clock);
            client = new GitHubApiClient(restClientBuilder, tokens, objectMapper);
            installation = client.installation();
        } catch (GitHubCredentialException | GitHubApiException refused) {
            return ConnectorCredentialProbeResult.rejected(GitHubErrorCodes.of(refused));
        }

        JsonNode account = installation.path("account");
        String connectionKey = account.path("id").asString("");
        String accountName = account.path("login").asString(connectionKey);
        String appName = installation.path("app_slug").asString("GitHub App");
        if (!"Organization".equals(account.path("type").asString(""))
                || connectionKey.isBlank()) {
            return ConnectorCredentialProbeResult.withoutContentAccess(
                    connectionKey,
                    accountName,
                    appName,
                    "organization_installation_required");
        }
        if (!canRead(installation.path("permissions").path("issues"))) {
            return ConnectorCredentialProbeResult.withoutContentAccess(
                    connectionKey,
                    accountName,
                    appName,
                    "issues_read_required");
        }

        try {
            List<JsonNode> repositories = client.repositories();
            if (repositories.stream().noneMatch(GitHubScopeBrowser::admissible)) {
                return ConnectorCredentialProbeResult.withoutContentAccess(
                        connectionKey,
                        accountName,
                        appName,
                        "no_admissible_repositories");
            }
        } catch (GitHubCredentialException | GitHubApiException refused) {
            return ConnectorCredentialProbeResult.withoutContentAccess(
                    connectionKey,
                    accountName,
                    appName,
                    GitHubErrorCodes.of(refused));
        }
        return ConnectorCredentialProbeResult.usable(connectionKey, accountName, appName);
    }

    private static boolean canRead(JsonNode permission) {
        String value = permission.asString("");
        return "read".equals(value) || "write".equals(value);
    }

}
