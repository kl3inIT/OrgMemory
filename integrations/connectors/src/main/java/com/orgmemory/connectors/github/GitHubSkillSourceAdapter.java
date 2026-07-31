package com.orgmemory.connectors.github;

import com.orgmemory.core.assetregistry.SkillGitHubSourcePort;
import com.orgmemory.core.assetregistry.SkillPackageSpec;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionConfiguration;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionDirectory;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import com.orgmemory.core.shared.error.BusinessUnavailableException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import com.orgmemory.core.shared.secret.SecretValue;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** GitHub transport for bounded, commit-pinned Skill repository imports. */
final class GitHubSkillSourceAdapter implements SkillGitHubSourcePort {

    private static final String API = "https://api.github.com";
    private static final String CODELOAD = "https://codeload.github.com";
    private static final int MAX_JSON_BYTES = 2 * 1024 * 1024;
    private static final int MAX_ARCHIVE_BYTES = 25 * 1024 * 1024;
    private static final String POLICY_VERSION = "skill-github-import-v1";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    private final ConnectorConnectionDirectory connections;
    private final PermissionAuditService audit;
    private final RestClient anonymousClient;
    private final RestClient downloadClient;
    private final RestClient.Builder authenticatedClientBuilder;
    private final ObjectMapper json;
    private final Clock clock;

    GitHubSkillSourceAdapter(
            ConnectorConnectionDirectory connections,
            PermissionAuditService audit,
            RestClient.Builder restClientBuilder) {
        this(
                connections,
                audit,
                noRedirect(restClientBuilder).build(),
                noRedirect(restClientBuilder).build(),
                noRedirect(restClientBuilder),
                new ObjectMapper(),
                Clock.systemUTC());
    }

    GitHubSkillSourceAdapter(
            ConnectorConnectionDirectory connections,
            PermissionAuditService audit,
            RestClient anonymousClient,
            RestClient downloadClient,
            RestClient.Builder authenticatedClientBuilder,
            ObjectMapper json,
            Clock clock) {
        this.connections = connections;
        this.audit = audit;
        this.anonymousClient = anonymousClient;
        this.downloadClient = downloadClient;
        this.authenticatedClientBuilder = authenticatedClientBuilder;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public FetchResult fetch(FetchRequest request) {
        Repository repository = Repository.parse(request.repository());
        String subpath = safeSubpath(request.subpath());
        ApiResponse publicRepository = getJson(
                anonymousClient, repositoryUri(repository), "", true);
        Access access;
        if (publicRepository.status().is2xxSuccessful()) {
            JsonNode metadata = parse(publicRepository.bytes(), "repository metadata");
            if (metadata.path("private").asBoolean(false)) {
                throw new BusinessValidationException(
                        "skill.github-private-connection-required",
                        "Choose an approved GitHub connection for a private repository");
            }
            access = new Access(
                    anonymousClient,
                    "",
                    SkillPackageSpec.Visibility.PUBLIC,
                    metadata.path("id").asString(""));
        } else if (isRateLimited(publicRepository)) {
            throw new BusinessUnavailableException(
                    "skill.github-rate-limited",
                    "GitHub rate-limited the repository request");
        } else if (isPrivateCandidate(publicRepository.status())) {
            access = privateAccess(request, repository);
        } else {
            throw sourceFailure(publicRepository.status());
        }

        JsonNode commit = requireJson(
                getJson(
                        access.client(),
                        commitUri(repository, request.revision()),
                        access.token(),
                        true),
                "repository revision");
        String revision = commit.path("sha").asString("").strip().toLowerCase();
        if (!revision.matches("[0-9a-f]{40}")) {
            throw new BusinessUnavailableException(
                    "skill.github-response-invalid",
                    "GitHub returned an invalid commit revision");
        }
        byte[] archive = access.visibility() == SkillPackageSpec.Visibility.PUBLIC
                ? publicArchive(repository, revision)
                : privateArchive(access, repository, revision);
        List<FetchedPackage> packages = GitHubSkillArchiveReader.read(archive, subpath);
        return new FetchResult(
                repository.fullName(), revision, access.visibility(), packages);
    }

    @Override
    public List<ConnectionOption> availableConnections(java.util.UUID organizationId) {
        return connections.configurations(organizationId, "github").stream()
                .filter(ConnectorConnectionConfiguration::credentialSet)
                .filter(configuration -> {
                    GitHubSkillImportSettings settings =
                            GitHubSkillImportSettings.from(configuration.sourceConfig());
                    return settings.valid() && settings.allowPrivateSkillImports();
                })
                .map(configuration -> new ConnectionOption(configuration.sourceConnectionKey()))
                .sorted(java.util.Comparator.comparing(ConnectionOption::key))
                .toList();
    }

    private Access privateAccess(FetchRequest request, Repository repository) {
        if (request.connectionKey().isBlank()) {
            throw new BusinessNotFoundException(
                    "skill.github-repository-unavailable",
                    "The repository is unavailable or requires an approved GitHub connection",
                    BusinessErrorExposure.OPAQUE_RESOURCE);
        }
        ConnectorConnectionConfiguration configuration = connections
                .configuration(request.organizationId(), "github", request.connectionKey())
                .orElseThrow(() -> new BusinessNotFoundException(
                        "skill.github-connection-unavailable",
                        "The GitHub connection is unavailable",
                        BusinessErrorExposure.OPAQUE_RESOURCE));
        GitHubSkillImportSettings settings =
                GitHubSkillImportSettings.from(configuration.sourceConfig());
        if (!settings.valid() || !settings.allowPrivateSkillImports()) {
            throw new BusinessValidationException(
                    "skill.github-private-import-disabled",
                    "An administrator must enable private Skill imports for this GitHub connection");
        }
        SecretValue credential = connections
                .resolveCredential(request.organizationId(), "github", request.connectionKey())
                .orElseThrow(() -> new BusinessValidationException(
                        "skill.github-credential-missing",
                        "The GitHub connection has no usable credential"));
        GitHubInstallationTokenSource tokens = new GitHubInstallationTokenSource(
                authenticatedClientBuilder,
                GitHubAppKey.parse(credential.expose()),
                json,
                clock);
        String token = tokens.accessToken();
        RestClient authenticated = authenticatedClientBuilder.clone().build();
        JsonNode metadata = requireJson(
                getJson(authenticated, repositoryUri(repository), token, true),
                "private repository metadata");
        String repositoryId = metadata.path("id").asString("");
        if (!settings.allowsRepository(repositoryId)) {
            auditCredentialUse(
                    request,
                    repository,
                    PermissionAuditDecision.DENY,
                    "REPOSITORY_NOT_APPROVED");
            throw new BusinessNotFoundException(
                    "skill.github-repository-unavailable",
                    "The repository is not selected for this GitHub connection",
                    BusinessErrorExposure.OPAQUE_RESOURCE);
        }
        auditCredentialUse(
                request,
                repository,
                PermissionAuditDecision.ALLOW,
                "PRIVATE_REPOSITORY_ACCESS");
        if (!metadata.path("private").asBoolean(false)) {
            return new Access(
                    authenticated, token, SkillPackageSpec.Visibility.PUBLIC, repositoryId);
        }
        return new Access(authenticated, token, SkillPackageSpec.Visibility.PRIVATE, repositoryId);
    }

    private byte[] publicArchive(Repository repository, String revision) {
        URI uri = UriComponentsBuilder.fromUriString(CODELOAD)
                .pathSegment(repository.owner(), repository.name(), "tar.gz", revision)
                .build()
                .encode()
                .toUri();
        return requireArchive(getBytes(downloadClient, uri, ""));
    }

    private byte[] privateArchive(Access access, Repository repository, String revision) {
        URI apiArchive = UriComponentsBuilder.fromUriString(API)
                .pathSegment("repos", repository.owner(), repository.name(), "tarball", revision)
                .build()
                .encode()
                .toUri();
        ApiResponse redirect = getBytes(access.client(), apiArchive, access.token());
        if (!redirect.status().is3xxRedirection()) {
            throw sourceFailure(redirect.status());
        }
        String location = redirect.headers().getFirst(HttpHeaders.LOCATION);
        URI codeload = requireCodeload(location);
        return requireArchive(getBytes(downloadClient, codeload, ""));
    }

    private JsonNode requireJson(ApiResponse response, String resource) {
        if (!response.status().is2xxSuccessful()) {
            throw sourceFailure(response.status());
        }
        return parse(response.bytes(), resource);
    }

    private byte[] requireArchive(ApiResponse response) {
        if (!response.status().is2xxSuccessful()) {
            throw sourceFailure(response.status());
        }
        return response.bytes();
    }

    private ApiResponse getJson(
            RestClient client, URI uri, String token, boolean jsonResponse) {
        ApiResponse response = exchange(client, uri, token, MAX_JSON_BYTES);
        if (jsonResponse && response.status().is2xxSuccessful()) {
            parse(response.bytes(), uri.getPath());
        }
        return response;
    }

    private ApiResponse getBytes(RestClient client, URI uri, String token) {
        return exchange(client, uri, token, MAX_ARCHIVE_BYTES);
    }

    private ApiResponse exchange(
            RestClient client, URI uri, String token, int maximumBytes) {
        requireAllowedUri(uri);
        try {
            return client.get()
                    .uri(uri)
                    .headers(headers -> {
                        headers.set(HttpHeaders.ACCEPT, "application/vnd.github+json");
                        headers.set("X-GitHub-Api-Version", GitHubInstallationTokenSource.API_VERSION);
                        if (!token.isBlank()) {
                            headers.setBearerAuth(token);
                        }
                    })
                    .exchange((request, response) -> new ApiResponse(
                            response.getStatusCode(),
                            response.getHeaders(),
                            readBounded(response.getBody(), maximumBytes)),
                            false);
        } catch (ResourceAccessException unavailable) {
            throw new BusinessUnavailableException(
                    "skill.github-unreachable",
                    "GitHub could not be reached",
                    unavailable);
        }
    }

    private JsonNode parse(byte[] body, String resource) {
        try {
            JsonNode parsed = json.readTree(body);
            if (parsed == null || !parsed.isObject()) {
                throw new IllegalArgumentException("not an object");
            }
            return parsed;
        } catch (RuntimeException unreadable) {
            throw new BusinessUnavailableException(
                    "skill.github-response-invalid",
                    "GitHub returned an unreadable " + resource,
                    unreadable);
        }
    }

    private static BusinessUnavailableException sourceFailure(HttpStatusCode status) {
        if (status.value() == 404) {
            return new BusinessUnavailableException(
                    "skill.github-repository-unavailable",
                    "The repository or revision is unavailable");
        }
        return new BusinessUnavailableException(
                "skill.github-request-failed",
                "GitHub rejected the repository request");
    }

    private static boolean isPrivateCandidate(HttpStatusCode status) {
        return status.value() == 403 || status.value() == 404;
    }

    private static boolean isRateLimited(ApiResponse response) {
        return response.status().value() == 429
                || "0".equals(response.headers().getFirst("X-RateLimit-Remaining"));
    }

    private static URI repositoryUri(Repository repository) {
        return UriComponentsBuilder.fromUriString(API)
                .pathSegment("repos", repository.owner(), repository.name())
                .build()
                .encode()
                .toUri();
    }

    private static URI commitUri(Repository repository, String revision) {
        return UriComponentsBuilder.fromUriString(API)
                .pathSegment("repos", repository.owner(), repository.name(), "commits", revision)
                .build()
                .encode()
                .toUri();
    }

    private static URI requireCodeload(String location) {
        if (location == null || location.isBlank()) {
            throw new BusinessUnavailableException(
                    "skill.github-redirect-invalid",
                    "GitHub returned an archive redirect without a location");
        }
        URI uri;
        try {
            uri = URI.create(location);
        } catch (IllegalArgumentException invalid) {
            throw new BusinessUnavailableException(
                    "skill.github-redirect-invalid",
                    "GitHub returned an invalid archive redirect",
                    invalid);
        }
        boolean defaultPort = uri.getPort() == -1 || uri.getPort() == 443;
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"codeload.github.com".equalsIgnoreCase(uri.getHost())
                || !defaultPort
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new BusinessUnavailableException(
                    "skill.github-redirect-invalid",
                    "GitHub returned an archive redirect outside codeload.github.com");
        }
        return uri;
    }

    private static void requireAllowedUri(URI uri) {
        String host = uri == null ? "" : uri.getHost();
        boolean allowedHost = "api.github.com".equalsIgnoreCase(host)
                || "codeload.github.com".equalsIgnoreCase(host);
        boolean defaultPort = uri != null && (uri.getPort() == -1 || uri.getPort() == 443);
        if (uri == null
                || !"https".equalsIgnoreCase(uri.getScheme())
                || !allowedHost
                || !defaultPort
                || uri.getUserInfo() != null
                || uri.getFragment() != null) {
            throw new BusinessValidationException(
                    "skill.github-uri-invalid",
                    "The GitHub request target is outside the approved host boundary");
        }
    }

    private static String safeSubpath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.strip().replace('\\', '/');
        if (normalized.startsWith("/")
                || normalized.endsWith("/")
                || normalized.contains("//")
                || List.of(normalized.split("/")).stream()
                        .anyMatch(segment -> segment.isBlank()
                                || ".".equals(segment)
                                || "..".equals(segment))) {
            throw new BusinessValidationException(
                    "skill.github-path-invalid",
                    "The repository path must be a safe relative directory");
        }
        return normalized;
    }

    private static byte[] readBounded(InputStream input, int maximumBytes) throws IOException {
        byte[] read = input.readNBytes(maximumBytes + 1);
        if (read.length > maximumBytes) {
            throw new BusinessValidationException(
                    "skill.github-archive-too-large",
                    "The GitHub repository archive exceeds its size limit");
        }
        return read;
    }

    private static RestClient.Builder noRedirect(RestClient.Builder template) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(client);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return template.clone().requestFactory(requestFactory);
    }

    private void auditCredentialUse(
            FetchRequest request,
            Repository repository,
            PermissionAuditDecision decision,
            String reasonCode) {
        audit.record(new PermissionAuditCommand(
                request.organizationId(),
                request.actorUserId(),
                "SKILL_GITHUB_IMPORT_CREDENTIAL_USE",
                "source_connection",
                request.connectionKey(),
                decision,
                reasonCode,
                POLICY_VERSION,
                null,
                repository.fullName()));
    }

    private record Access(
            RestClient client,
            String token,
            SkillPackageSpec.Visibility visibility,
            String repositoryId) {
    }

    private record ApiResponse(
            HttpStatusCode status,
            HttpHeaders headers,
            byte[] bytes) {

        ApiResponse {
            headers = HttpHeaders.readOnlyHttpHeaders(headers);
            bytes = bytes.clone();
        }

        String body() {
            return new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }

    private record Repository(String owner, String name) {

        static Repository parse(String value) {
            String normalized = value == null ? "" : value.strip();
            if (normalized.startsWith("https://github.com/")) {
                normalized = normalized.substring("https://github.com/".length());
            }
            normalized = normalized.replaceFirst("[\\s/]+$", "");
            if (normalized.endsWith(".git")) {
                normalized = normalized.substring(0, normalized.length() - 4);
            }
            String[] segments = normalized.split("/");
            if (segments.length != 2
                    || !validSegment(segments[0])
                    || !validSegment(segments[1])) {
                throw new BusinessValidationException(
                        "skill.github-repository-invalid",
                        "Enter a GitHub repository as owner/repository or its HTTPS URL");
            }
            return new Repository(segments[0], segments[1]);
        }

        String fullName() {
            return owner + "/" + name;
        }

        private static boolean validSegment(String value) {
            return value.matches("[A-Za-z0-9_.-]+")
                    && !".".equals(value)
                    && !"..".equals(value);
        }
    }
}
