package com.orgmemory.connectors.github;

import com.orgmemory.core.knowledge.acl.SourcePrincipalKind;

import com.orgmemory.connectors.PollingConnectorBatchSource;
import com.orgmemory.core.knowledge.connector.ConnectorAclGrant;
import com.orgmemory.core.knowledge.connector.ConnectorCaptureStatus;
import com.orgmemory.core.knowledge.connector.ConnectorComponentState;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.connector.ConnectorContentItem;
import com.orgmemory.core.knowledge.connector.ConnectorContractVersions;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlConfiguration;
import com.orgmemory.core.knowledge.connector.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.connector.ConnectorMembershipItem;
import com.orgmemory.core.knowledge.connector.ConnectorMembershipMember;
import com.orgmemory.core.knowledge.connector.ConnectorObjectDirectory;
import com.orgmemory.core.knowledge.connector.ConnectorPermissionItem;
import com.orgmemory.core.knowledge.connector.ConnectorSyncComponent;
import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Mirrors private GitHub issue and pull-request descriptions together with GitHub's complete,
 * effective repository-reader set.
 *
 * <p>A repository reader group is the authorization boundary. GitHub's collaborators endpoint
 * already resolves direct grants, teams (including child teams), organization defaults, owners,
 * and enterprise roles. Rebuilding those paths here would be both less complete and less safe,
 * so each work item grants one stable {@code repository:{id}:readers} group whose independently
 * captured membership is that authoritative effective set.
 */
final class GitHubConnectorBatchSource extends PollingConnectorBatchSource<GitHubApiClient> {

    private static final Logger log = LoggerFactory.getLogger(GitHubConnectorBatchSource.class);
    private static final String SOURCE_SYSTEM = GitHubSourceProfile.SOURCE_SYSTEM;
    private static final String READER_PREFIX = "repository:";
    private static final String READER_SUFFIX = ":readers";

    private final ConnectorObjectDirectory objects;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    GitHubConnectorBatchSource(
            ConnectorConnectionDirectory connections,
            ConnectorObjectDirectory objects,
            RestClient.Builder restClientBuilder) {
        this(connections, objects, restClientBuilder, new ObjectMapper(), Clock.systemUTC());
    }

    GitHubConnectorBatchSource(
            ConnectorConnectionDirectory connections,
            ConnectorObjectDirectory objects,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Clock clock) {
        super(connections, clock);
        this.objects = objects;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    ConnectorCrawlBatch batchFor(ConnectorCrawlConfiguration configuration) {
        return pollConnection(configuration);
    }

    @Override
    protected String sourceSystem() {
        return SOURCE_SYSTEM;
    }

    @Override
    protected String displayName() {
        return "GitHub";
    }

    @Override
    protected String crawlCursorPrefix() {
        return "github-";
    }

    @Override
    protected GitHubApiClient createClient(
            ConnectorCrawlConfiguration configuration, SecretValue credential) {
        GitHubAppKey appKey = GitHubAppKey.parse(credential.expose());
        GitHubInstallationTokenSource tokens =
                new GitHubInstallationTokenSource(restClientBuilder, appKey, objectMapper, clock);
        return new GitHubApiClient(restClientBuilder, tokens, objectMapper);
    }

    @Override
    protected RuntimeException missingCredential(ConnectorCrawlConfiguration configuration) {
        return new GitHubCredentialException(
                "No GitHub App credential is stored for connection "
                        + configuration.sourceConnectionKey(),
                "no_credential");
    }

    @Override
    protected boolean isExpectedConnectorFailure(RuntimeException failure) {
        return failure instanceof GitHubCredentialException || failure instanceof GitHubApiException;
    }

    @Override
    protected String errorCodeOf(RuntimeException failure) {
        return GitHubErrorCodes.of(failure);
    }

    @Override
    protected CrawlResult crawl(
            GitHubApiClient client,
            ConnectorCrawlConfiguration configuration,
            boolean contentDue) {
        requireInstallation(client, configuration);
        GitHubCrawlSettings settings = GitHubCrawlSettings.from(configuration.sourceConfig());
        List<JsonNode> repositories = repositoriesInScope(client, settings);
        return contentDue
                ? contentCrawl(client, configuration, repositories, settings)
                : permissionsCrawl(client, configuration, repositories);
    }

    private CrawlResult permissionsCrawl(
            GitHubApiClient client,
            ConnectorCrawlConfiguration configuration,
            List<JsonNode> repositories) {
        Crawl crawl = captureReaders(client, repositories);
        crawl.grantKnownObjects(objects.activeObjectIds(
                configuration.organizationId(),
                SOURCE_SYSTEM,
                configuration.sourceConnectionKey()));
        return result(
                batch(configuration, crawl, List.of(), false, false),
                crawl.failedRepositoryCount(),
                repositories.size(),
                "repositories");
    }

    private CrawlResult contentCrawl(
            GitHubApiClient client,
            ConnectorCrawlConfiguration configuration,
            List<JsonNode> repositories,
            GitHubCrawlSettings settings) {
        Crawl crawl = captureReaders(client, repositories);
        for (JsonNode repository : repositories) {
            String repositoryId = repository.path("id").asString("");
            if (!crawl.hasCompleteAudience(repositoryId)) {
                continue;
            }
            String owner = repository.path("owner").path("login").asString("");
            String name = repository.path("name").asString("");
            try {
                GitHubApiClient.PagedItems issues =
                        client.issues(owner, name, settings.maxItemsPerRepository());
                issues.items().forEach(issue -> crawl.addIssue(repository, issue));
                if (issues.truncated()) {
                    crawl.contentIncomplete();
                }
            } catch (GitHubApiException unreadable) {
                log.warn("GitHub repository {}/{} content was skipped: {}",
                        owner, name, unreadable.getMessage());
                crawl.contentIncomplete();
                crawl.repositoryFailed(repositoryId);
            }
        }
        boolean enumeratedScope = settings.selectsEverything();
        boolean complete = enumeratedScope && crawl.contentComplete && crawl.accessComplete;
        return result(
                batch(configuration, crawl, crawl.contents, true, complete),
                crawl.failedRepositoryCount(),
                repositories.size(),
                "repositories");
    }

    private Crawl captureReaders(GitHubApiClient client, List<JsonNode> repositories) {
        Crawl crawl = new Crawl();
        for (JsonNode repository : repositories) {
            String repositoryId = repository.path("id").asString("");
            String fullName = repository.path("full_name").asString(repositoryId);
            try {
                List<JsonNode> collaborators = client.collaborators(
                        repository.path("owner").path("login").asString(""),
                        repository.path("name").asString(""));
                crawl.observeAudience(repositoryId, fullName, collaborators);
            } catch (GitHubApiException unreadable) {
                log.warn("GitHub repository {} readers were not authoritative: {}",
                        fullName, unreadable.getMessage());
                crawl.observeIncompleteAudience(
                        repositoryId,
                        fullName,
                        "GITHUB_COLLABORATORS_NOT_FULLY_READ");
                crawl.repositoryFailed(repositoryId);
            }
        }
        return crawl;
    }

    private static void requireInstallation(
            GitHubApiClient client,
            ConnectorCrawlConfiguration configuration) {
        JsonNode installation = client.installation();
        JsonNode account = installation.path("account");
        if (!"Organization".equals(account.path("type").asString(""))) {
            throw new GitHubCredentialException(
                    "The GitHub App must be installed on an organization",
                    "organization_installation_required");
        }
        if (!account.path("id").asString("").equals(configuration.sourceConnectionKey())) {
            // Validate identity before repository reads so replacing a connection's key cannot
            // silently repoint already-governed data.
            throw new GitHubCredentialException(
                    "The GitHub App installation does not match this connection",
                    "connection_mismatch");
        }
        String issues = installation.path("permissions").path("issues").asString("");
        if (!"read".equals(issues) && !"write".equals(issues)) {
            throw new GitHubCredentialException(
                    "The GitHub App installation needs Issues read permission",
                    "issues_read_required");
        }
    }

    private static List<JsonNode> repositoriesInScope(
            GitHubApiClient client,
            GitHubCrawlSettings settings) {
        if (!settings.valid()) {
            throw new GitHubApiException(
                    "The GitHub source configuration is invalid",
                    "invalid_source_config");
        }
        List<JsonNode> installed = client.repositories();
        if (settings.repositoryIds().isEmpty()) {
            return installed.stream()
                    .filter(GitHubScopeBrowser::admissible)
                    .sorted(Comparator.comparing(
                            repository -> repository.path("full_name").asString(""),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        installed.forEach(repository -> byId.put(repository.path("id").asString(""), repository));
        List<JsonNode> selected = new ArrayList<>();
        for (String repositoryId : settings.repositoryIds()) {
            JsonNode repository = byId.get(repositoryId);
            if (repository == null) {
                throw new GitHubApiException(
                        "Configured GitHub repository " + repositoryId
                                + " is not accessible to this installation",
                        "repository_not_accessible");
            }
            if (!GitHubScopeBrowser.admissible(repository)) {
                throw new GitHubApiException(
                        "Configured GitHub repository "
                                + repository.path("full_name").asString(repositoryId)
                                + " is not a private organization repository with Issues enabled",
                        "repository_not_admissible");
            }
            selected.add(repository);
        }
        selected.sort(Comparator.comparing(
                repository -> repository.path("full_name").asString(""),
                String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(selected);
    }

    private ConnectorCrawlBatch batch(
            ConnectorCrawlConfiguration configuration,
            Crawl crawl,
            List<ConnectorContentItem> contents,
            boolean contentIncluded,
            boolean complete) {
        List<ConnectorComponentState> states = new ArrayList<>();
        if (contentIncluded) {
            states.add(crawl.contentComplete
                    ? ConnectorComponentState.complete(
                            ConnectorSyncComponent.CONTENT,
                            contentCursor(crawl))
                    : ConnectorComponentState.incomplete(
                            ConnectorSyncComponent.CONTENT,
                            contentCursor(crawl),
                            "GITHUB_CONTENT_NOT_FULLY_READ"));
        }
        states.add(crawl.accessComplete
                ? ConnectorComponentState.complete(
                        ConnectorSyncComponent.PERMISSION,
                        permissionCursor(crawl))
                : ConnectorComponentState.incomplete(
                        ConnectorSyncComponent.PERMISSION,
                        permissionCursor(crawl),
                        "GITHUB_COLLABORATORS_NOT_FULLY_READ"));
        states.add(crawl.accessComplete
                ? ConnectorComponentState.complete(
                        ConnectorSyncComponent.MEMBERSHIP,
                        membershipCursor(crawl))
                : ConnectorComponentState.incomplete(
                        ConnectorSyncComponent.MEMBERSHIP,
                        membershipCursor(crawl),
                        "GITHUB_COLLABORATORS_NOT_FULLY_READ"));
        return new ConnectorCrawlBatch(
                configuration.organizationId(),
                SOURCE_SYSTEM,
                configuration.sourceConnectionKey(),
                configuration.knowledgeSpaceId(),
                configuration.actorUserId(),
                crawlCursor(states),
                ConnectorContractVersions.supported(),
                crawl.identities(),
                crawl.memberships(),
                contents,
                crawl.permissions,
                List.of(),
                states,
                complete);
    }

    private static String contentCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        crawl.contents.stream()
                .sorted(Comparator.comparing(ConnectorContentItem::externalObjectId))
                .forEach(content -> material
                        .append(content.externalObjectId())
                        .append('=')
                        .append(content.contentRevision())
                        .append(';'));
        material.append("complete=").append(crawl.contentComplete);
        return "github-content-" + sha256(material.toString());
    }

    private static String permissionCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        crawl.permissions.stream()
                .sorted(Comparator.comparing(ConnectorPermissionItem::externalObjectId))
                .forEach(permission -> material
                        .append(permission.externalObjectId())
                        .append('@')
                        .append(permission.grants())
                        .append(';'));
        material.append("complete=").append(crawl.accessComplete);
        return "github-permission-" + sha256(material.toString());
    }

    private static String membershipCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        crawl.audiences.values().forEach(audience -> material
                .append(audience.groupId())
                .append('@')
                .append(audience.memberIds())
                .append('#')
                .append(audience.status())
                .append(';'));
        material.append("complete=").append(crawl.accessComplete);
        return "github-membership-" + sha256(material.toString());
    }

    private static String readerGroupId(String repositoryId) {
        return READER_PREFIX + repositoryId + READER_SUFFIX;
    }

    private static String sha256(String value) {
        return com.orgmemory.core.shared.Digests.sha256(value);
    }

    private record GitHubUser(String id, String login) {
    }

    private record RepositoryAudience(
            String repositoryId,
            String fullName,
            String groupId,
            ConnectorCaptureStatus status,
            String incompleteReason,
            List<String> memberIds) {
    }

    private static final class Crawl {

        private final Map<String, GitHubUser> users = new LinkedHashMap<>();
        private final Map<String, RepositoryAudience> audiences = new LinkedHashMap<>();
        private final List<ConnectorContentItem> contents = new ArrayList<>();
        private final List<ConnectorPermissionItem> permissions = new ArrayList<>();
        private final Set<String> failedRepositoryIds = new LinkedHashSet<>();
        private boolean contentComplete = true;
        private boolean accessComplete = true;

        private void repositoryFailed(String repositoryId) {
            failedRepositoryIds.add(repositoryId);
        }

        private int failedRepositoryCount() {
            return failedRepositoryIds.size();
        }

        private void contentIncomplete() {
            contentComplete = false;
        }

        private void observeAudience(
                String repositoryId,
                String fullName,
                List<JsonNode> collaborators) {
            Set<String> memberIds = new LinkedHashSet<>();
            boolean representable = true;
            for (JsonNode collaborator : collaborators) {
                String id = collaborator.path("id").asString("");
                String login = collaborator.path("login").asString(id);
                String type = collaborator.path("type").asString("");
                if (id.isBlank() || (!"User".equals(type) && !"EnterpriseUser".equals(type))) {
                    representable = false;
                    continue;
                }
                users.putIfAbsent(id, new GitHubUser(id, login));
                memberIds.add(id);
            }
            if (!representable) {
                observeIncompleteAudience(
                        repositoryId,
                        fullName,
                        "GITHUB_COLLABORATOR_IDENTITY_UNREPRESENTABLE");
                return;
            }
            audiences.put(repositoryId, new RepositoryAudience(
                    repositoryId,
                    fullName,
                    readerGroupId(repositoryId),
                    ConnectorCaptureStatus.COMPLETE,
                    null,
                    List.copyOf(memberIds)));
        }

        private void observeIncompleteAudience(
                String repositoryId,
                String fullName,
                String reason) {
            accessComplete = false;
            audiences.put(repositoryId, new RepositoryAudience(
                    repositoryId,
                    fullName,
                    readerGroupId(repositoryId),
                    ConnectorCaptureStatus.INCOMPLETE,
                    reason,
                    List.of()));
        }

        private boolean hasCompleteAudience(String repositoryId) {
            RepositoryAudience audience = audiences.get(repositoryId);
            return audience != null && audience.status() == ConnectorCaptureStatus.COMPLETE;
        }

        private void addIssue(JsonNode repository, JsonNode issue) {
            String repositoryId = repository.path("id").asString("");
            String issueId = issue.path("id").asString("");
            String number = issue.path("number").asString("");
            String title = issue.path("title").asString("").trim();
            if (repositoryId.isBlank() || issueId.isBlank() || title.isBlank()) {
                contentIncomplete();
                return;
            }
            String fullName = repository.path("full_name").asString(repositoryId);
            String kind = issue.has("pull_request") ? "Pull request" : "Issue";
            String author = issue.path("user").path("login").asString("unknown");
            String state = issue.path("state").asString("unknown");
            String body = issue.path("body").asString("").trim();
            String rendered = kind + " #" + number
                    + "\nRepository: " + fullName
                    + "\nState: " + state
                    + "\nAuthor: @" + author
                    + (body.isBlank() ? "" : "\n\n" + body);
            String externalObjectId = repositoryId + "__" + issueId;
            contents.add(new ConnectorContentItem(
                    externalObjectId,
                    "[" + fullName + " #" + number + "] " + title,
                    rendered,
                    sha256(rendered)));
            permissions.add(permission(externalObjectId, repositoryId));
        }

        private void grantKnownObjects(List<String> knownObjectIds) {
            for (String externalObjectId : knownObjectIds) {
                int separator = externalObjectId.indexOf("__");
                if (separator <= 0) {
                    continue;
                }
                String repositoryId = externalObjectId.substring(0, separator);
                if (hasCompleteAudience(repositoryId)) {
                    permissions.add(permission(externalObjectId, repositoryId));
                }
            }
        }

        private static ConnectorPermissionItem permission(
                String externalObjectId,
                String repositoryId) {
            return new ConnectorPermissionItem(
                    externalObjectId,
                    List.of(new ConnectorAclGrant(
                            SourcePrincipalKind.SOURCE_GROUP,
                            readerGroupId(repositoryId),
                            AccessGate.ALLOW)));
        }

        private List<ConnectorIdentityItem> identities() {
            List<ConnectorIdentityItem> identities = new ArrayList<>();
            users.values().forEach(user -> identities.add(new ConnectorIdentityItem(
                    SourcePrincipalKind.SOURCE_USER,
                    user.id(),
                    null,
                    user.login(),
                    false,
                    null,
                    null)));
            audiences.values().forEach(audience -> identities.add(new ConnectorIdentityItem(
                    SourcePrincipalKind.SOURCE_GROUP,
                    audience.groupId(),
                    null,
                    audience.fullName() + " readers",
                    false,
                    null,
                    null)));
            return List.copyOf(identities);
        }

        private List<ConnectorMembershipItem> memberships() {
            return audiences.values().stream()
                    .map(audience -> new ConnectorMembershipItem(
                            audience.groupId(),
                            audience.status(),
                            audience.incompleteReason(),
                            audience.memberIds().stream()
                                    .map(id -> new ConnectorMembershipMember(
                                            SourcePrincipalKind.SOURCE_USER,
                                            id))
                                    .toList()))
                    .toList();
        }
    }
}
