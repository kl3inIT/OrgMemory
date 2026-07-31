package com.orgmemory.connectors.github;

import com.orgmemory.core.knowledge.connector.ConnectorScope;
import com.orgmemory.core.knowledge.connector.ConnectorScopeBrowser;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Lists repositories selected for the installation and whether this connector can mirror them. */
public class GitHubScopeBrowser implements ConnectorScopeBrowser {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public GitHubScopeBrowser(RestClient.Builder restClientBuilder) {
        this(restClientBuilder, new ObjectMapper(), Clock.systemUTC());
    }

    GitHubScopeBrowser(
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
    public List<ConnectorScope> scopes(SecretValue credential, String sourceConfig) {
        Objects.requireNonNull(credential, "credential");
        GitHubAppKey key = GitHubAppKey.parse(credential.expose());
        GitHubApiClient client = new GitHubApiClient(
                restClientBuilder,
                new GitHubInstallationTokenSource(restClientBuilder, key, objectMapper, clock),
                objectMapper);
        List<ConnectorScope> scopes = new ArrayList<>();
        for (JsonNode repository : client.repositories()) {
            String id = repository.path("id").asString("");
            if (id.isBlank()) {
                continue;
            }
            String name = repository.path("full_name").asString(id);
            scopes.add(admissible(repository)
                    ? ConnectorScope.reachable(id, name)
                    : ConnectorScope.barred(id, name, instruction(repository)));
        }
        scopes.sort(Comparator.comparing(ConnectorScope::displayName, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(scopes);
    }

    static boolean admissible(JsonNode repository) {
        return repository.path("id").asString("").matches("[1-9][0-9]*")
                && !repository.path("name").asString("").isBlank()
                && !repository.path("owner").path("login").asString("").isBlank()
                && repository.path("private").asBoolean(false)
                && "private".equals(repository.path("visibility").asString(""))
                && "Organization".equals(repository.path("owner").path("type").asString(""))
                && repository.path("has_issues").asBoolean(false);
    }

    private static String instruction(JsonNode repository) {
        if (!"Organization".equals(repository.path("owner").path("type").asString(""))) {
            return "Install the GitHub App on an organization; user-owned repositories are not supported.";
        }
        if (!repository.path("private").asBoolean(false)
                || !"private".equals(repository.path("visibility").asString(""))) {
            return "This connector mirrors private repository readers; public and internal visibility are not supported.";
        }
        if (!repository.path("has_issues").asBoolean(false)) {
            return "Enable Issues for this repository before selecting it.";
        }
        return "GitHub did not return the stable repository identity metadata this connector requires.";
    }
}
