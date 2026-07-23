package com.orgmemory.connectors.googledrive;

import com.orgmemory.core.knowledge.ConnectorAclGrant;
import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.ConnectorConnectionFailure;
import com.orgmemory.core.knowledge.ConnectorContentItem;
import com.orgmemory.core.knowledge.ConnectorContractVersions;
import com.orgmemory.core.knowledge.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.ConnectorCrawlConfiguration;
import com.orgmemory.core.knowledge.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.ConnectorPermissionItem;
import com.orgmemory.core.knowledge.ConnectorPoll;
import com.orgmemory.core.knowledge.SourcePrincipalKind;
import com.orgmemory.core.shared.secret.SecretValue;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Crawls the Google Drives an administrator has enabled into the crawl-batch contract the
 * governed ledger already ingests.
 *
 * <p>The unit is a file, keyed on Drive's own file id, with a hash of the extracted text as the
 * content revision. Drive's {@code modifiedTime} is not that revision: it moves when somebody
 * changes sharing, and re-materializing a document because its permissions changed would pay
 * for chunking and embedding to arrive at the identical text.
 *
 * <p>The care here, as in the Slack adapter, is the completeness claim. Declaring a crawl
 * complete authorizes the ledger to retire everything the crawl did not mention, so it is
 * claimed only when this really did enumerate the connection: no folder filter, nothing skipped,
 * nothing truncated.
 */
class GoogleDriveConnectorBatchSource implements ConnectorBatchSource {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveConnectorBatchSource.class);
    private static final String SOURCE_SYSTEM = GoogleDriveSourceProfile.SOURCE_SYSTEM;
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    /** Above this share of unreadable files the run is a failure, not a crawl. */
    private static final double FAILED_FILE_ABORT_SHARE = 0.5;

    private final ConnectorConnectionDirectory connections;
    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Instant> contentCrawlDueAt = new ConcurrentHashMap<>();

    GoogleDriveConnectorBatchSource(
            ConnectorConnectionDirectory connections, RestClient.Builder restClientBuilder) {
        this(connections, restClientBuilder, new ObjectMapper(), Clock.systemUTC());
    }

    GoogleDriveConnectorBatchSource(
            ConnectorConnectionDirectory connections,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Clock clock) {
        this.connections = connections;
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ConnectorPoll pendingBatches() {
        List<ConnectorCrawlBatch> batches = new ArrayList<>();
        List<ConnectorConnectionFailure> unavailable = new ArrayList<>();
        for (ConnectorCrawlConfiguration configuration : connections.enabledCrawls(SOURCE_SYSTEM)) {
            try {
                batches.add(batchFor(configuration));
            } catch (GoogleDriveCredentialException | GoogleDriveApiException failure) {
                log.warn("Google Drive connection {} produced no batch this poll: {}",
                        configuration.sourceConnectionKey(), failure.getMessage());
                unavailable.add(new ConnectorConnectionFailure(
                        configuration.organizationId(),
                        SOURCE_SYSTEM,
                        configuration.sourceConnectionKey(),
                        errorCodeOf(failure),
                        failure.getMessage()));
            }
        }
        return new ConnectorPoll(batches, unavailable);
    }

    private static String errorCodeOf(RuntimeException failure) {
        if (failure instanceof GoogleDriveCredentialException refused) {
            return refused.errorCode();
        }
        if (failure instanceof GoogleDriveApiException refused && refused.errorCode() != null) {
            return refused.errorCode();
        }
        return "drive_error";
    }

    /**
     * What one connection owes this poll.
     *
     * <p>Access changes far more often than content, so between content crawls this reads
     * metadata only — one listing carries every file's permissions, and no document body is
     * fetched at all. That is the same split the Slack adapter makes for the same reason, and
     * here it is even cheaper: the permissions ride along with the listing rather than costing
     * their own call.
     */
    ConnectorCrawlBatch batchFor(ConnectorCrawlConfiguration configuration) {
        GoogleDriveCrawlSettings settings = GoogleDriveCrawlSettings.from(configuration.sourceConfig());
        GoogleDriveApiClient client = clientFor(configuration, settings);

        String due = dueKey(configuration);
        Instant now = clock.instant();
        boolean contentDue = !now.isBefore(contentCrawlDueAt.getOrDefault(due, Instant.EPOCH));
        if (contentDue) {
            contentCrawlDueAt.put(due, now.plus(configuration.contentCrawlInterval()));
        }
        return crawl(client, configuration, settings, contentDue);
    }

    private GoogleDriveApiClient clientFor(
            ConnectorCrawlConfiguration configuration, GoogleDriveCrawlSettings settings) {
        SecretValue credential = connections
                .resolveCredential(
                        configuration.organizationId(), SOURCE_SYSTEM, configuration.sourceConnectionKey())
                .orElseThrow(() -> new GoogleDriveCredentialException(
                        "No Google Drive credential is stored for connection "
                                + configuration.sourceConnectionKey(),
                        "no_credential"));
        GoogleAccessTokenSource tokens = new GoogleAccessTokenSource(
                restClientBuilder,
                GoogleServiceAccountKey.parse(credential.expose()),
                DRIVE_SCOPE,
                settings.impersonatedUser(),
                clock);
        return new GoogleDriveApiClient(restClientBuilder, tokens, objectMapper);
    }

    /** Two tenants may key a connection the same way, so the cadence is remembered per tenant. */
    private static String dueKey(ConnectorCrawlConfiguration configuration) {
        return configuration.organizationId() + "/" + configuration.sourceConnectionKey();
    }

    private ConnectorCrawlBatch crawl(
            GoogleDriveApiClient client,
            ConnectorCrawlConfiguration configuration,
            GoogleDriveCrawlSettings settings,
            boolean readContent) {
        Crawl crawl = new Crawl();
        if (!settings.enumeratesEverything()) {
            // A folder filter means this crawl looked at part of the connection, so it cannot
            // speak for the rest of it — and the ledger must not retire what it did not look at.
            crawl.incomplete();
        }

        List<JsonNode> files =
                client.listFiles(queryFor(settings), settings.includeSharedDrives(), settings.maxFiles());
        if (files.size() >= settings.maxFiles()) {
            // Hitting the bound means there may be more. Indistinguishable downstream from a
            // mass deletion, so the claim goes.
            crawl.incomplete();
        }

        int considered = 0;
        int failed = 0;
        for (JsonNode file : files) {
            if (file.path("trashed").asBoolean(false)
                    || !GoogleDriveDocumentTypes.isIndexable(file.path("mimeType").asString(""))) {
                continue;
            }
            considered++;
            try {
                observe(client, file, crawl, readContent);
            } catch (GoogleDriveApiException failure) {
                // A file the account cannot read is not a file that vanished. Losing one costs
                // this crawl its completeness claim rather than costing the Drive its index.
                log.warn("Google Drive file {} was skipped: {}",
                        file.path("id").asString(""), failure.getMessage());
                crawl.incomplete();
                failed++;
            }
        }
        abortIfMostlyFailed(failed, considered);

        // A permissions-only pass says nothing about whether content still exists — it never
        // opened a document — so it must not authorize retiring anything.
        //
        // Unlike Slack's, it needs nothing from the ledger to do its job: one Drive listing
        // carries every file's own sharing, so the objects it grants over are the ones it just
        // saw. An object the ledger holds that this listing did not return is deliberately left
        // out of the payload, which keeps whatever was last sealed for it — an empty grant list
        // would instead assert that nobody may read it.
        if (!readContent) {
            crawl.incomplete();
        }

        return new ConnectorCrawlBatch(
                configuration.organizationId(),
                SOURCE_SYSTEM,
                configuration.sourceConnectionKey(),
                configuration.knowledgeSpaceId(),
                configuration.actorUserId(),
                crawlCursor(crawl),
                ConnectorContractVersions.supported(),
                crawl.identities(),
                crawl.contents,
                crawl.permissions,
                List.of(),
                crawl.complete);
    }

    private void observe(
            GoogleDriveApiClient client, JsonNode file, Crawl crawl, boolean readContent) {
        String fileId = file.path("id").asString("");
        String title = file.path("name").asString(fileId);

        crawl.observeOwner(file);
        List<ConnectorAclGrant> grants = GoogleDrivePermissionMapper.grantsFor(file);
        crawl.observePrincipals(file, grants);
        crawl.permissions.add(new ConnectorPermissionItem(fileId, grants));

        if (!readContent) {
            return;
        }
        String body = extractText(client, file);
        if (body.isBlank()) {
            // An empty document is not a failure and not evidence. Indexing a blank body would
            // put an unanswerable chunk in retrieval; it stays in the permission payload, so a
            // complete crawl still does not retire it.
            return;
        }
        crawl.contents.add(new ConnectorContentItem(fileId, title, body, sha256(body)));
    }

    private static String extractText(GoogleDriveApiClient client, JsonNode file) {
        String fileId = file.path("id").asString("");
        String mimeType = file.path("mimeType").asString("");
        String exportTarget = GoogleDriveDocumentTypes.exportTargetFor(mimeType);
        return exportTarget == null
                ? client.downloadText(fileId)
                : client.exportText(fileId, exportTarget);
    }

    /** Only the types this adapter indexes, and only inside the folders asked for. */
    private static String queryFor(GoogleDriveCrawlSettings settings) {
        StringBuilder query = new StringBuilder("trashed = false and ")
                .append(GoogleDriveDocumentTypes.indexableTypeClause());
        if (!settings.folderIds().isEmpty()) {
            query.append(" and (");
            for (int index = 0; index < settings.folderIds().size(); index++) {
                query.append(index == 0 ? "" : " or ")
                        .append("'").append(settings.folderIds().get(index)).append("' in parents");
            }
            query.append(")");
        }
        return query.toString();
    }

    /**
     * Stops a run in which most files failed rather than reporting it as a crawl.
     *
     * <p>Nothing is destroyed by carrying on — the completeness claim is already withdrawn — but
     * a batch would be checkpointed and the connection would look healthy while its index quietly
     * went stale. Failing leaves this connection without a batch, so the work is retried.
     */
    private static void abortIfMostlyFailed(int failed, int considered) {
        if (failed > 0 && failed >= considered * FAILED_FILE_ABORT_SHARE) {
            throw new GoogleDriveApiException(
                    "Google Drive crawl abandoned: " + failed + " of " + considered
                            + " files could not be read");
        }
    }

    private static String crawlCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        crawl.contents.forEach(content ->
                material.append(content.externalObjectId()).append('=')
                        .append(content.contentRevision()).append(';'));
        crawl.permissions.forEach(permission ->
                material.append(permission.externalObjectId()).append('@')
                        .append(permission.grants().size()).append(';'));
        material.append("complete=").append(crawl.complete);
        return "google-drive-" + sha256(material.toString());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the platform", impossible);
        }
    }

    /** Accumulates one crawl's payloads and whether it may still claim to have seen everything. */
    private static final class Crawl {

        private final Map<String, DriveUser> observedUsers = new LinkedHashMap<>();
        private final Map<String, Set<String>> observedDomainGroups = new LinkedHashMap<>();
        private final Map<String, Set<String>> observedGroups = new LinkedHashMap<>();
        private final List<ConnectorContentItem> contents = new ArrayList<>();
        private final List<ConnectorPermissionItem> permissions = new ArrayList<>();
        private boolean complete = true;

        private void incomplete() {
            complete = false;
        }

        /** The owner is a user even when nothing was shared with anybody else. */
        private void observeOwner(JsonNode file) {
            JsonNode owner = file.path("owners").path(0);
            String email = owner.path("emailAddress").asString("").strip().toLowerCase();
            if (!email.isEmpty()) {
                observe(email, owner.path("displayName").asString(email));
            }
        }

        private void observePrincipals(JsonNode file, List<ConnectorAclGrant> grants) {
            for (ConnectorAclGrant grant : grants) {
                String key = grant.principalExternalKey();
                if (grant.principalKind() == SourcePrincipalKind.SOURCE_USER) {
                    observe(key, key);
                } else if (!key.startsWith(GoogleDrivePermissionMapper.DOMAIN_GROUP_PREFIX)) {
                    // A real Google group. Drive does not report its members, so it is declared
                    // with none and grants only once somebody maps it deliberately.
                    observedGroups.computeIfAbsent(key, ignored -> new LinkedHashSet<>());
                }
            }
            GoogleDrivePermissionMapper.domainsGrantedBy(file)
                    .forEach(domain -> observedDomainGroups.computeIfAbsent(
                            GoogleDrivePermissionMapper.domainGroupKey(domain),
                            ignored -> new LinkedHashSet<>()));
        }

        private void observe(String email, String displayName) {
            observedUsers.putIfAbsent(email, new DriveUser(email, displayName));
        }

        /**
         * Every principal this crawl saw. A domain group's membership is the users observed at
         * that domain — the Drive API cannot enumerate a domain, so this under-grants and says so
         * rather than inventing members.
         */
        private List<ConnectorIdentityItem> identities() {
            List<ConnectorIdentityItem> identities = new ArrayList<>();
            observedUsers.values().forEach(user -> identities.add(new ConnectorIdentityItem(
                    SourcePrincipalKind.SOURCE_USER,
                    user.email(),
                    user.email(),
                    user.displayName(),
                    // Google verifies address ownership before an account can exist, which is
                    // the same reasoning that makes Slack's observed users SSO-verified.
                    true,
                    null,
                    null,
                    List.of())));
            observedGroups.forEach((key, members) -> identities.add(new ConnectorIdentityItem(
                    SourcePrincipalKind.SOURCE_GROUP, key, null, key, false, null, null, List.copyOf(members))));
            observedDomainGroups.keySet().forEach(key -> identities.add(new ConnectorIdentityItem(
                    SourcePrincipalKind.SOURCE_GROUP,
                    key,
                    null,
                    "Everyone at " + key.substring(GoogleDrivePermissionMapper.DOMAIN_GROUP_PREFIX.length()),
                    false,
                    null,
                    null,
                    membersAtDomain(key))));
            return identities;
        }

        private List<String> membersAtDomain(String domainGroupKey) {
            String domain = "@" + domainGroupKey.substring(
                    GoogleDrivePermissionMapper.DOMAIN_GROUP_PREFIX.length());
            return observedUsers.keySet().stream().filter(email -> email.endsWith(domain)).toList();
        }
    }

    private record DriveUser(String email, String displayName) {
    }
}
