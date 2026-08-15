package com.orgmemory.connectors.googledrive;

import com.orgmemory.core.knowledge.acl.SourcePrincipalKind;

import com.orgmemory.connectors.PollingConnectorBatchSource;
import com.orgmemory.core.knowledge.connector.ConnectorAclGrant;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.connector.ConnectorComponentState;
import com.orgmemory.core.knowledge.connector.ConnectorContentItem;
import com.orgmemory.core.knowledge.connector.ConnectorContractVersions;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlBatch;
import com.orgmemory.core.knowledge.connector.ConnectorCrawlConfiguration;
import com.orgmemory.core.knowledge.connector.ConnectorIdentityItem;
import com.orgmemory.core.knowledge.connector.ConnectorMembershipItem;
import com.orgmemory.core.knowledge.connector.ConnectorPermissionItem;
import com.orgmemory.core.knowledge.connector.ConnectorCaptureStatus;
import com.orgmemory.core.knowledge.connector.ConnectorSyncComponent;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
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
 * nothing truncated, and nothing Google itself called an incomplete search.
 */
class GoogleDriveConnectorBatchSource extends PollingConnectorBatchSource<GoogleDriveApiClient> {

    private static final Logger log = LoggerFactory.getLogger(GoogleDriveConnectorBatchSource.class);
    private static final String SOURCE_SYSTEM = GoogleDriveSourceProfile.SOURCE_SYSTEM;
    private static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.readonly";
    private static final String FOLDER_TYPE = "application/vnd.google-apps.folder";
    /** How many parent ids go into one query, so a folder scope cannot outgrow Drive's limit. */
    private static final int PARENTS_PER_QUERY = 25;
    /** A bound on folder expansion, so a pathological tree cannot spin here forever. */
    private static final int MAX_FOLDERS = 500;
    private static final String CONTENT_BUDGET_EXHAUSTED =
            "GOOGLE_DRIVE_CONTENT_BUDGET_EXHAUSTED";

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    GoogleDriveConnectorBatchSource(
            ConnectorConnectionDirectory connections, RestClient.Builder restClientBuilder) {
        this(connections, restClientBuilder, new ObjectMapper(), Clock.systemUTC());
    }

    GoogleDriveConnectorBatchSource(
            ConnectorConnectionDirectory connections,
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            Clock clock) {
        super(connections, clock);
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected String errorCodeOf(RuntimeException failure) {
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
     * fetched at all. That is the same split the Slack adapter makes for the same reason.
     *
     * <p>The content cadence advances only once the crawl has produced a batch. Advancing it
     * first would let a Drive that was briefly unreachable suppress its own content crawl until
     * the interval came round again, quietly degrading every poll in between to permissions.
     */
    ConnectorCrawlBatch batchFor(ConnectorCrawlConfiguration configuration) {
        return pollConnection(configuration);
    }

    @Override
    protected String sourceSystem() {
        return SOURCE_SYSTEM;
    }

    @Override
    protected String displayName() {
        return "Google Drive";
    }

    @Override
    protected String crawlCursorPrefix() {
        return "google-drive-";
    }

    @Override
    protected GoogleDriveApiClient createClient(
            ConnectorCrawlConfiguration configuration, SecretValue credential) {
        GoogleDriveCrawlSettings settings = GoogleDriveCrawlSettings.from(configuration.sourceConfig());
        GoogleAccessTokenSource tokens = new GoogleAccessTokenSource(
                restClientBuilder,
                GoogleServiceAccountKey.parse(credential.expose()),
                DRIVE_SCOPE,
                settings.impersonatedUser(),
                clock);
        return new GoogleDriveApiClient(restClientBuilder, tokens, objectMapper);
    }

    @Override
    protected String clientConfigurationMaterial(ConnectorCrawlConfiguration configuration) {
        return GoogleDriveCrawlSettings.from(configuration.sourceConfig()).impersonatedUser();
    }

    @Override
    protected RuntimeException missingCredential(ConnectorCrawlConfiguration configuration) {
        return new GoogleDriveCredentialException(
                "No Google Drive credential is stored for connection "
                        + configuration.sourceConnectionKey(),
                "no_credential");
    }

    @Override
    protected boolean isExpectedConnectorFailure(RuntimeException failure) {
        return failure instanceof GoogleDriveCredentialException
                || failure instanceof GoogleDriveApiException;
    }

    @Override
    protected CrawlResult crawl(
            GoogleDriveApiClient client,
            ConnectorCrawlConfiguration configuration,
            boolean contentDue) {
        GoogleDriveCrawlSettings settings = GoogleDriveCrawlSettings.from(configuration.sourceConfig());
        Crawl crawl = new Crawl();
        if (!settings.enumeratesEverything()) {
            // A folder filter means this crawl looked at part of the connection, so it cannot
            // speak for the rest of it — and the ledger must not retire what it did not look at.
            crawl.incomplete();
        }

        List<JsonNode> files = listFiles(client, settings, crawl);
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
                observe(client, file, crawl, contentDue, settings);
            } catch (GoogleDriveApiException failure) {
                // A file the account cannot read is not a file that vanished. Losing one costs
                // this crawl its completeness claim rather than costing the Drive its index.
                log.warn("Google Drive file {} was skipped: {}",
                        file.path("id").asString(""), failure.getMessage());
                crawl.incomplete();
                failed++;
            }
        }
        // A permissions-only pass says nothing about whether content still exists — it never
        // opened a document — so it must not authorize retiring anything.
        //
        // Unlike Slack's, it needs nothing from the ledger to do its job: one Drive listing
        // carries every file's own sharing, so the objects it grants over are the ones it just
        // saw. An object the ledger holds that this listing did not return is deliberately left
        // out of the payload, which keeps whatever was last sealed for it — an empty grant list
        // would instead assert that nobody may read it.
        List<ConnectorComponentState> componentStates = new ArrayList<>();
        if (contentDue) {
            componentStates.add(crawl.contentComplete
                    ? ConnectorComponentState.complete(
                            ConnectorSyncComponent.CONTENT, contentCursor(crawl))
                    : ConnectorComponentState.incomplete(
                            ConnectorSyncComponent.CONTENT,
                            contentCursor(crawl),
                            CONTENT_BUDGET_EXHAUSTED));
        }
        componentStates.add(crawl.permissionComplete
                ? ConnectorComponentState.complete(
                        ConnectorSyncComponent.PERMISSION, permissionCursor(crawl))
                : ConnectorComponentState.incomplete(
                        ConnectorSyncComponent.PERMISSION,
                        permissionCursor(crawl),
                        "GOOGLE_DRIVE_SHARING_NOT_FULLY_READ"));
        componentStates.add(ConnectorComponentState.incomplete(
                ConnectorSyncComponent.MEMBERSHIP,
                membershipCursor(crawl),
                "GOOGLE_DIRECTORY_MEMBERSHIP_NOT_CAPTURED"));

        ConnectorCrawlBatch batch = new ConnectorCrawlBatch(
                configuration.organizationId(),
                SOURCE_SYSTEM,
                configuration.sourceConnectionKey(),
                configuration.knowledgeSpaceId(),
                configuration.actorUserId(),
                crawlCursor(componentStates),
                ConnectorContractVersions.supported(),
                crawl.identities(),
                crawl.memberships(),
                crawl.contents,
                crawl.permissions,
                List.of(),
                componentStates,
                contentDue && crawl.complete);
        return result(batch, failed, considered, "files");
    }

    /**
     * Every file in scope, which for a folder filter means the whole subtree beneath it.
     *
     * <p>Drive reads {@code 'X' in parents} as a file's immediate parent, so a folder scope has
     * to be expanded into the folders under it before anything is listed. An administrator who
     * picked a folder meant its contents, not the handful of files that happen to sit at its top.
     */
    private static List<JsonNode> listFiles(
            GoogleDriveApiClient client, GoogleDriveCrawlSettings settings, Crawl crawl) {
        List<String> parents = folderScope(client, settings, crawl);
        String typeClause = GoogleDriveDocumentTypes.indexableTypeClause();
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        if (parents.isEmpty()) {
            collect(client, "trashed = false and " + typeClause, settings, crawl, byId);
            return List.copyOf(byId.values());
        }
        for (List<String> chunk : chunked(parents)) {
            collect(
                    client,
                    "trashed = false and " + typeClause + " and " + parentClause(chunk),
                    settings,
                    crawl,
                    byId);
            if (byId.size() >= settings.maxFiles()) {
                break;
            }
        }
        return List.copyOf(byId.values());
    }

    private static void collect(
            GoogleDriveApiClient client,
            String query,
            GoogleDriveCrawlSettings settings,
            Crawl crawl,
            Map<String, JsonNode> byId) {
        int remaining = settings.maxFiles() - byId.size();
        if (remaining <= 0) {
            return;
        }
        GoogleDriveApiClient.FileListing listing =
                client.listFiles(query, settings.includeSharedDrives(), remaining);
        if (listing.incompleteSearch()) {
            // Google itself says it did not search everywhere it was asked to. Whatever it left
            // out is missing from this crawl, and absence is what authorizes retiring.
            crawl.incomplete();
        }
        for (JsonNode file : listing.files()) {
            String id = file.path("id").asString("");
            if (!id.isEmpty()) {
                // A file reachable from two scoped folders comes back twice; it is one object.
                byId.putIfAbsent(id, file);
            }
        }
    }

    /** The configured folders plus every folder beneath them, with cycles walked only once. */
    private static List<String> folderScope(
            GoogleDriveApiClient client, GoogleDriveCrawlSettings settings, Crawl crawl) {
        if (settings.folderIds().isEmpty()) {
            return List.of();
        }
        Set<String> visited = new LinkedHashSet<>(settings.folderIds());
        Deque<String> frontier = new ArrayDeque<>(settings.folderIds());
        while (!frontier.isEmpty()) {
            List<String> chunk = new ArrayList<>();
            while (!frontier.isEmpty() && chunk.size() < PARENTS_PER_QUERY) {
                chunk.add(frontier.poll());
            }
            GoogleDriveApiClient.FileListing children = client.listFiles(
                    "trashed = false and mimeType = '" + FOLDER_TYPE + "' and " + parentClause(chunk),
                    settings.includeSharedDrives(),
                    MAX_FOLDERS);
            if (children.incompleteSearch()) {
                crawl.incomplete();
            }
            for (JsonNode folder : children.files()) {
                String id = folder.path("id").asString("");
                if (id.isEmpty() || !visited.add(id)) {
                    continue;
                }
                if (visited.size() > MAX_FOLDERS) {
                    // The scope is larger than this will walk, so it did not see all of it.
                    crawl.incomplete();
                    return List.copyOf(visited);
                }
                frontier.add(id);
            }
        }
        return List.copyOf(visited);
    }

    private static List<List<String>> chunked(List<String> ids) {
        List<List<String>> chunks = new ArrayList<>();
        for (int start = 0; start < ids.size(); start += PARENTS_PER_QUERY) {
            chunks.add(ids.subList(start, Math.min(start + PARENTS_PER_QUERY, ids.size())));
        }
        return chunks;
    }

    private static String parentClause(List<String> parentIds) {
        StringBuilder clause = new StringBuilder("(");
        for (int index = 0; index < parentIds.size(); index++) {
            clause.append(index == 0 ? "" : " or ")
                    .append("'").append(parentIds.get(index)).append("' in parents");
        }
        return clause.append(")").toString();
    }

    private void observe(
            GoogleDriveApiClient client,
            JsonNode file,
            Crawl crawl,
            boolean readContent,
            GoogleDriveCrawlSettings settings) {
        String fileId = file.path("id").asString("");
        String title = file.path("name").asString(fileId);

        List<JsonNode> permissions = permissionsOf(client, file);
        if (permissions == null) {
            // Sharing this crawl could not read. Leaving the object out of the payload keeps
            // whatever was last sealed for it; sending an empty grant list would instead assert
            // that Drive says nobody may read it, which is a claim this crawl cannot make.
            log.warn("Google Drive file {} was left out: its sharing could not be read", fileId);
            crawl.incomplete();
            crawl.permissionIncomplete();
            return;
        }
        crawl.observeOwner(file);
        List<ConnectorAclGrant> grants = GoogleDrivePermissionMapper.grantsFor(permissions);
        crawl.observePrincipals(permissions, grants);
        crawl.permissions.add(new ConnectorPermissionItem(fileId, grants));

        if (!readContent) {
            return;
        }
        if (crawl.contentBudgetExhausted()) {
            return;
        }
        if (exceedsSizeBound(file, settings)) {
            // Refusing to read a file is this adapter's own policy, not a fact about the file,
            // so unlike a type it does not index, it must not license retiring what is already
            // held for this object.
            log.warn("Google Drive file {} is larger than the configured bound and was not read", fileId);
            crawl.incomplete();
            return;
        }
        String body = extractText(client, file);
        if (body.isBlank()) {
            // An empty document is not a failure and not evidence. Indexing a blank body would
            // put an unanswerable chunk in retrieval; it stays in the permission payload, so a
            // complete crawl still does not retire it.
            return;
        }
        if (!crawl.reserveContentBytes(utf8Length(body), settings.maxBatchBytes())) {
            log.warn("Google Drive content budget was exhausted while admitting file {}", fileId);
            return;
        }
        crawl.contents.add(new ConnectorContentItem(fileId, title, body, sha256(body)));
    }

    /**
     * One file's sharing, or null when it could not be established.
     *
     * <p>Drive does not inline {@code permissions} for an item in a shared drive; it returns
     * {@code permissionIds} and expects a separate call. Reading the absent field as "shared with
     * nobody" would seal a generation denying everyone, so the ids are followed instead, and a
     * file with neither is reported as unreadable rather than as empty.
     */
    private static List<JsonNode> permissionsOf(GoogleDriveApiClient client, JsonNode file) {
        JsonNode inline = file.path("permissions");
        if (inline.isArray() && !inline.isEmpty()) {
            List<JsonNode> permissions = new ArrayList<>();
            inline.forEach(permissions::add);
            return permissions;
        }
        JsonNode ids = file.path("permissionIds");
        if (!ids.isArray() || ids.isEmpty()) {
            // Nothing inline and nothing to follow. A file genuinely shared with nobody but its
            // owner still reports the owner's own permission, so this is an absence of evidence.
            return inline.isArray() ? List.of() : null;
        }
        try {
            return client.listPermissions(file.path("id").asString(""));
        } catch (GoogleDriveApiException refused) {
            return null;
        }
    }

    /**
     * Whether Drive's own metadata puts this file past the bound. Google-native documents report
     * no size because they are exported rather than downloaded; the client's hard cap on a
     * response body is what covers those.
     */
    private static boolean exceedsSizeBound(JsonNode file, GoogleDriveCrawlSettings settings) {
        JsonNode size = file.path("size");
        if (size.isMissingNode() || size.isNull()) {
            return false;
        }
        try {
            // Drive reports size as a decimal string, not a number.
            return Long.parseLong(size.asString("0").strip()) > settings.maxFileBytes();
        } catch (NumberFormatException unreadable) {
            return false;
        }
    }

    private static String extractText(GoogleDriveApiClient client, JsonNode file) {
        String fileId = file.path("id").asString("");
        String mimeType = file.path("mimeType").asString("");
        String exportTarget = GoogleDriveDocumentTypes.exportTargetFor(mimeType);
        return exportTarget == null
                ? client.downloadText(fileId)
                : client.exportText(fileId, exportTarget);
    }

    /**
     * Counts encoded bytes without allocating a second full copy of every retained body.
     * {@link String#getBytes(java.nio.charset.Charset)} replaces an unpaired surrogate with one
     * byte under UTF-8, which is mirrored here.
     */
    private static long utf8Length(String value) {
        long bytes = 0;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (current <= 0x7f) {
                bytes++;
            } else if (current <= 0x7ff) {
                bytes += 2;
            } else if (Character.isHighSurrogate(current)
                    && index + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else if (Character.isSurrogate(current)) {
                bytes++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    /**
     * A fingerprint of everything this batch asserts, which is what lets the driver recognise a
     * batch it has already ingested.
     *
     * <p>It has to cover the grants themselves and not merely how many there are. Swapping one
     * reader for another leaves the count alone, and a cursor that only counted would let the
     * driver skip the batch as already done — leaving the removed reader with access and the
     * added one without, which is the exact convergence the permissions cadence exists for.
     * Everything is sorted first, because Drive's ordering is not a change.
     */
    private static String contentCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        Map<String, String> contents = new TreeMap<>();
        crawl.contents.forEach(content -> contents.put(
                content.externalObjectId(), content.contentRevision() + '/' + sha256(content.title())));
        contents.forEach((id, revision) -> material.append(id).append('=').append(revision).append(';'));
        material.append("enumerationComplete=").append(crawl.complete);
        if (!crawl.contentComplete) {
            material.append(";contentComplete=false");
        }
        return "google-drive-content-" + sha256(material.toString());
    }

    private static String permissionCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder();
        Map<String, String> permissions = new TreeMap<>();
        crawl.permissions.forEach(permission -> permissions.put(
                permission.externalObjectId(),
                permission.grants().stream()
                        .map(grant -> grant.principalKind() + ":" + grant.principalNativeId()
                                + ":" + grant.gate())
                        .sorted()
                        .reduce((left, right) -> left + "," + right)
                        .orElse("")));
        permissions.forEach((id, grants) -> material.append(id).append('@').append(grants).append(';'));

        Map<String, String> identities = new TreeMap<>();
        crawl.identities().forEach(identity -> identities.put(
                identity.kind() + ":" + identity.nativePrincipalId(),
                identity.email() == null ? "" : identity.email()));
        identities.forEach((key, alias) -> material.append(key).append('#').append(alias).append(';'));
        material.append("enumerationComplete=").append(crawl.complete);
        material.append("permissionComplete=").append(crawl.permissionComplete);
        return "google-drive-permission-" + sha256(material.toString());
    }

    private static String membershipCursor(Crawl crawl) {
        StringBuilder material = new StringBuilder(membershipCursorMaterial(crawl.memberships()));
        Map<String, String> identities = new TreeMap<>();
        crawl.identities().forEach(identity -> identities.put(
                identity.kind() + ":" + identity.nativePrincipalId(),
                identity.email() == null ? "" : identity.email()));
        identities.forEach((key, alias) -> material.append(key).append('#').append(alias).append(';'));
        return "google-drive-membership-" + sha256(material.toString());
    }

    static String membershipCursorMaterial(List<ConnectorMembershipItem> capturedMemberships) {
        Map<String, String> memberships = new TreeMap<>();
        capturedMemberships.forEach(membership -> memberships.put(
                membership.groupNativePrincipalId(),
                membership.captureStatus()
                        + ":" + membership.incompleteReason()
                        + ":" + membership.members().stream()
                                .map(member -> member.kind() + ":" + member.nativePrincipalId())
                                .sorted()
                                .reduce((left, right) -> left + "," + right)
                                .orElse("")));
        StringBuilder material = new StringBuilder();
        memberships.forEach((key, evidence) ->
                material.append(key).append('%').append(evidence).append(';'));
        return material.toString();
    }

    private static String sha256(String value) {
        return com.orgmemory.core.shared.Digests.sha256(value);
    }

    /** Accumulates one crawl's payloads and whether it may still claim to have seen everything. */
    private static final class Crawl {

        private final Map<String, DriveUser> observedUsers = new LinkedHashMap<>();
        private final Map<String, DriveGroup> observedGroups = new LinkedHashMap<>();
        private final List<ConnectorContentItem> contents = new ArrayList<>();
        private final List<ConnectorPermissionItem> permissions = new ArrayList<>();
        private boolean complete = true;
        private boolean contentComplete = true;
        private boolean permissionComplete = true;
        private long retainedContentBytes;

        private void incomplete() {
            complete = false;
        }

        private void permissionIncomplete() {
            permissionComplete = false;
        }

        private boolean contentBudgetExhausted() {
            return !contentComplete;
        }

        private boolean reserveContentBytes(long bodyBytes, long maxBatchBytes) {
            if (bodyBytes > maxBatchBytes - retainedContentBytes) {
                incomplete();
                contentComplete = false;
                return false;
            }
            retainedContentBytes += bodyBytes;
            return true;
        }

        /** The owner is a user even when nothing was shared with anybody else. */
        private void observeOwner(JsonNode file) {
            JsonNode owner = file.path("owners").path(0);
            String nativeId = owner.path("permissionId").asString("").strip();
            String email = owner.path("emailAddress").asString("").strip().toLowerCase();
            if (!nativeId.isEmpty()) {
                observeUser(nativeId, email, owner.path("displayName").asString(email));
            }
        }

        private void observePrincipals(List<JsonNode> filePermissions, List<ConnectorAclGrant> grants) {
            Set<String> granted = new LinkedHashSet<>();
            for (ConnectorAclGrant grant : grants) {
                granted.add(grant.principalKind() + ":" + grant.principalNativeId());
            }
            for (JsonNode permission : filePermissions) {
                String nativeId = GoogleDrivePermissionMapper.principalNativeIdOf(permission);
                if (nativeId == null) {
                    continue;
                }
                SourcePrincipalKind kind = GoogleDrivePermissionMapper.kindOf(permission);
                if (!granted.contains(kind + ":" + nativeId)) {
                    continue;
                }
                String email = permission.path("emailAddress").asString("").strip().toLowerCase();
                if (kind == SourcePrincipalKind.SOURCE_USER) {
                    observeUser(nativeId, email, permission.path("displayName").asString(email));
                } else {
                    String displayName = "domain".equals(permission.path("type").asString(""))
                            ? "Everyone at " + permission.path("domain").asString(nativeId)
                            : email.isEmpty() ? nativeId : email;
                    observedGroups.putIfAbsent(nativeId, new DriveGroup(nativeId, displayName));
                }
            }
        }

        private void observeUser(String nativeId, String email, String displayName) {
            observedUsers.putIfAbsent(nativeId, new DriveUser(nativeId, email, displayName));
        }

        /**
         * Every principal this crawl saw, keyed by Drive's immutable permission id. Email and
         * display name are aliases only and never define authorization identity.
         */
        private List<ConnectorIdentityItem> identities() {
            List<ConnectorIdentityItem> identities = new ArrayList<>();
            observedUsers.values().forEach(user -> identities.add(new ConnectorIdentityItem(
                    SourcePrincipalKind.SOURCE_USER,
                    user.nativeId(),
                    user.email(),
                    user.displayName(),
                    // Google verifies address ownership before an account can exist, which is
                    // the same reasoning that makes Slack's observed users SSO-verified.
                    true,
                    null,
                    null)));
            observedGroups.values().forEach(group -> identities.add(new ConnectorIdentityItem(
                    SourcePrincipalKind.SOURCE_GROUP,
                    group.nativeId(),
                    null,
                    group.displayName(),
                    false,
                    null,
                    null)));
            return identities;
        }

        private List<ConnectorMembershipItem> memberships() {
            return observedGroups.values().stream()
                    .map(group -> new ConnectorMembershipItem(
                            group.nativeId(),
                            ConnectorCaptureStatus.INCOMPLETE,
                            "GOOGLE_DIRECTORY_MEMBERSHIP_NOT_CAPTURED",
                            List.of()))
                    .toList();
        }
    }

    private record DriveUser(String nativeId, String email, String displayName) {
    }

    private record DriveGroup(String nativeId, String displayName) {
    }
}
