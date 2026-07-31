package com.orgmemory.core.knowledge.connector;

import com.orgmemory.core.knowledge.acl.SourceConnectionPrincipalQuery;
import com.orgmemory.core.knowledge.acl.SourceConnectionPrincipalSummary;
import com.orgmemory.core.knowledge.acl.SourcePrincipalKind;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceService;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import com.orgmemory.core.shared.secret.SecretCipher;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Configuring a source connection: which workspace, with which credential, into which Knowledge
 * Space, on what cadence. This is what replaces editing environment variables and restarting,
 * so a change here takes effect on the next poll.
 *
 * <p>The credential is write-only across this facade. It goes in through {@link #setCredential}
 * and comes out only through {@link #resolveCredential}, which exists for the adapter that has
 * to authenticate; nothing that builds an administrator's view can reach it, and no method
 * returns it alongside anything else.
 *
 * <p>It implements {@link ConnectorConnectionDirectory} rather than having a second class read
 * the same rows, so what a worker sees of a connection and what an administrator sets on it can
 * never drift apart. The port is the narrow half a crawl is allowed to know about.
 */
@Service
public class SourceConnectionAdminService implements ConnectorConnectionDirectory {

    static final String POLICY_VERSION = "connection-admin-v1";
    private static final String IDENTITY_TRUST_POLICY_VERSION = "permissions-admin-v1";

    private final SourceConnectionRepository connections;
    private final SourceConnectionCredentialRepository credentials;
    private final KnowledgeSpaceService knowledgeSpaces;
    private final SecretCipher cipher;
    private final SourceConnectionPrincipalQuery principals;
    private final AppUserRepository users;
    private final PermissionAuditService audit;

    SourceConnectionAdminService(
            SourceConnectionRepository connections,
            SourceConnectionCredentialRepository credentials,
            KnowledgeSpaceService knowledgeSpaces,
            SecretCipher cipher,
            SourceConnectionPrincipalQuery principals,
            AppUserRepository users,
            PermissionAuditService audit) {
        this.connections = connections;
        this.credentials = credentials;
        this.knowledgeSpaces = knowledgeSpaces;
        this.cipher = cipher;
        this.principals = principals;
        this.users = users;
        this.audit = audit;
    }

    /** The connection as an administrator sees it, or empty when none has been configured. */
    @Transactional(readOnly = true)
    public Optional<SourceConnectionConfigurationView> describe(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        return find(organizationId, sourceSystem, sourceConnectionKey).map(this::toView);
    }

    /** Every connection of one source system in this organization. */
    @Transactional(readOnly = true)
    public List<SourceConnectionConfigurationView> list(UUID organizationId, String sourceSystem) {
        return connections
                .findByOrganizationIdAndSourceSystemOrderBySourceConnectionKeyAsc(
                        organizationId, requireText(sourceSystem, "sourceSystem"))
                .stream()
                .map(this::toView)
                .toList();
    }

    /** Every observed connection with its standing trust decision and ACL mapping tally. */
    @Transactional(readOnly = true)
    public List<SourceConnectionView> listConnections(UUID organizationId) {
        Map<ConnectionKey, SourceConnection> decided = connections
                .findByOrganizationId(organizationId)
                .stream()
                .collect(Collectors.toMap(ConnectionKey::ofConnection, Function.identity()));
        Map<ConnectionKey, ConnectionTally> tallies = new HashMap<>();
        for (SourceConnectionPrincipalSummary principal : principals.list(organizationId)) {
            tallies.computeIfAbsent(
                            ConnectionKey.ofPrincipal(principal),
                            key -> new ConnectionTally())
                    .observe(principal);
        }
        decided.keySet().forEach(key ->
                tallies.computeIfAbsent(key, unused -> new ConnectionTally()));
        return tallies.entrySet().stream()
                .map(entry -> entry.getValue().toView(entry.getKey(), decided.get(entry.getKey())))
                .sorted(CONNECTION_ORDER)
                .toList();
    }

    /** Records standing connection trust without retroactively mapping any principal. */
    @Transactional
    public SourceConnectionView setIdentityTrust(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            SourceIdentityTrust identityTrust,
            UUID decidedByUserId) {
        if (identityTrust == null) {
            throw new BusinessValidationException(
                    "source-connection.identity-trust-required",
                    "An identity trust level is required");
        }
        String system = requireSourceText(sourceSystem, "source system");
        String connectionKey = requireSourceText(sourceConnectionKey, "source connection key");
        if (!isActiveInOrg(decidedByUserId, organizationId)) {
            throw new BusinessValidationException(
                    "source-connection.deciding-user-invalid",
                    "The deciding user is not active in this organization");
        }

        SourceConnection connection = connections
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKey(
                        organizationId, system, connectionKey)
                .orElseGet(() -> new SourceConnection(organizationId, system, connectionKey));
        connection.decideTrust(identityTrust, decidedByUserId, Instant.now());
        connections.save(connection);
        audit.record(new PermissionAuditCommand(
                organizationId,
                decidedByUserId,
                "SOURCE_CONNECTION_TRUST",
                "SOURCE_CONNECTION",
                system + "/" + connectionKey,
                PermissionAuditDecision.ALLOW,
                "IDENTITY_TRUST_" + identityTrust.name(),
                IDENTITY_TRUST_POLICY_VERSION,
                null,
                null));

        return listConnections(organizationId).stream()
                .filter(view -> view.sourceSystem().equals(system)
                        && view.sourceConnectionKey().equals(connectionKey))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("The trust decision was not persisted"));
    }

    /** Records how a connection should be crawled, creating it when necessary. */
    @Transactional
    public SourceConnectionConfigurationView configure(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            boolean crawlEnabled,
            UUID knowledgeSpaceId,
            UUID actorUserId,
            String sourceConfig,
            Duration contentCrawlInterval,
            UUID adminUserId) {
        String system = requireText(sourceSystem, "sourceSystem");
        String key = requireText(sourceConnectionKey, "sourceConnectionKey");
        if (crawlEnabled && (knowledgeSpaceId == null || actorUserId == null)) {
            throw new BusinessValidationException(
                    "connection.crawl-target-required",
                    "A crawl needs a Knowledge Space to publish into and a user to publish as");
        }
        if (knowledgeSpaceId != null) {
            knowledgeSpaces.requireInOrganization(organizationId, knowledgeSpaceId);
        }
        SourceConnection connection = find(organizationId, system, key)
                .orElseGet(() -> connections.save(
                        new SourceConnection(organizationId, system, key)));
        connection.configureCrawl(
                crawlEnabled,
                knowledgeSpaceId,
                actorUserId,
                sourceConfig,
                (int) requireInterval(contentCrawlInterval).toSeconds(),
                adminUserId,
                Instant.now());
        connections.save(connection);
        record(
                organizationId,
                adminUserId,
                system,
                key,
                "SOURCE_CONNECTION_CONFIGURE",
                crawlEnabled ? "CRAWL_ENABLED" : "CRAWL_DISABLED");
        return toView(connection);
    }

    /** Stores a credential for the connection, replacing any previous one. */
    @Transactional
    public void setCredential(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            SecretValue token,
            UUID adminUserId) {
        Objects.requireNonNull(token, "token");
        String system = requireText(sourceSystem, "sourceSystem");
        String key = requireText(sourceConnectionKey, "sourceConnectionKey");
        SourceConnection connection = find(organizationId, system, key)
                .orElseGet(() -> connections.save(
                        new SourceConnection(organizationId, system, key)));
        Instant now = Instant.now();
        credentials.findByOrganizationIdAndSourceConnectionId(organizationId, connection.getId())
                .ifPresentOrElse(
                        existing -> {
                            existing.replaceWith(cipher.encrypt(token), adminUserId, now);
                            credentials.save(existing);
                        },
                        () -> credentials.save(new SourceConnectionCredential(
                                organizationId,
                                connection.getId(),
                                cipher.encrypt(token),
                                adminUserId,
                                now)));
        record(
                organizationId,
                adminUserId,
                system,
                key,
                "SOURCE_CONNECTION_CREDENTIAL",
                "CREDENTIAL_SET");
    }

    @Transactional
    public void forgetCredential(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            UUID adminUserId) {
        String system = requireText(sourceSystem, "sourceSystem");
        String key = requireText(sourceConnectionKey, "sourceConnectionKey");
        find(organizationId, system, key).ifPresent(connection ->
                credentials.deleteByOrganizationIdAndSourceConnectionId(
                        organizationId, connection.getId()));
        record(
                organizationId,
                adminUserId,
                system,
                key,
                "SOURCE_CONNECTION_CREDENTIAL",
                "CREDENTIAL_CLEARED");
    }

    /** The credential an adapter needs to authenticate. The only read path for a stored secret. */
    @Override
    @Transactional(readOnly = true)
    public Optional<SecretValue> resolveCredential(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        return find(
                        organizationId,
                        requireText(sourceSystem, "sourceSystem"),
                        requireText(sourceConnectionKey, "sourceConnectionKey"))
                .flatMap(connection -> credentials.findByOrganizationIdAndSourceConnectionId(
                        organizationId, connection.getId()))
                .map(credential -> cipher.decrypt(credential.stored()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConnectorConnectionConfiguration> configuration(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        return describe(organizationId, sourceSystem, sourceConnectionKey)
                .map(SourceConnectionAdminService::configuration);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConnectorConnectionConfiguration> configurations(
            UUID organizationId, String sourceSystem) {
        return list(organizationId, sourceSystem).stream()
                .map(SourceConnectionAdminService::configuration)
                .toList();
    }

    private static ConnectorConnectionConfiguration configuration(
            SourceConnectionConfigurationView view) {
        return new ConnectorConnectionConfiguration(
                view.sourceSystem(),
                view.sourceConnectionKey(),
                view.sourceConfig(),
                view.credentialSet());
    }

    /** Every enabled connection of one source system across tenants. */
    @Override
    @Transactional(readOnly = true)
    public List<ConnectorCrawlConfiguration> enabledCrawls(String sourceSystem) {
        return connections
                .findBySourceSystemAndCrawlEnabledTrue(requireText(sourceSystem, "sourceSystem"))
                .stream()
                .map(connection -> new ConnectorCrawlConfiguration(
                        connection.getOrganizationId(),
                        connection.getSourceSystem(),
                        connection.getSourceConnectionKey(),
                        connection.getKnowledgeSpaceId(),
                        connection.getActorUserId(),
                        connection.getSourceConfig(),
                        Duration.ofSeconds(connection.getContentCrawlIntervalSeconds()),
                        connection.getContentCrawlRequestedAt()))
                .toList();
    }

    /** Asks for a content crawl on the next poll rather than at the next interval. */
    @Transactional
    public void requestContentCrawl(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            UUID adminUserId) {
        String system = requireText(sourceSystem, "sourceSystem");
        String key = requireText(sourceConnectionKey, "sourceConnectionKey");
        SourceConnection connection = find(organizationId, system, key)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "connection.not-found",
                        "The source connection is not available",
                        BusinessErrorExposure.OPAQUE_RESOURCE));
        connection.requestContentCrawl(Instant.now());
        connections.save(connection);
        record(
                organizationId,
                adminUserId,
                system,
                key,
                "SOURCE_CONNECTION_CRAWL",
                "CRAWL_REQUESTED");
    }

    private Optional<SourceConnection> find(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        return connections.findByOrganizationIdAndSourceSystemAndSourceConnectionKey(
                organizationId, sourceSystem.trim(), sourceConnectionKey.trim());
    }

    private SourceConnectionConfigurationView toView(SourceConnection connection) {
        var credential = credentials.findByOrganizationIdAndSourceConnectionId(
                connection.getOrganizationId(), connection.getId());
        return new SourceConnectionConfigurationView(
                connection.getSourceSystem(),
                connection.getSourceConnectionKey(),
                connection.getIdentityTrust(),
                connection.isCrawlEnabled(),
                connection.getKnowledgeSpaceId(),
                connection.getActorUserId(),
                connection.getSourceConfig(),
                connection.getContentCrawlIntervalSeconds(),
                credential.isPresent(),
                credential.map(SourceConnectionCredential::getSetByUserId).orElse(null),
                credential.map(SourceConnectionCredential::getSetAt).orElse(null),
                connection.getCrawlConfiguredByUserId(),
                connection.getCrawlConfiguredAt());
    }

    private void record(
            UUID organizationId,
            UUID adminUserId,
            String system,
            String key,
            String operation,
            String reasonCode) {
        audit.record(new PermissionAuditCommand(
                organizationId,
                adminUserId,
                operation,
                "SOURCE_CONNECTION",
                system + "/" + key,
                PermissionAuditDecision.ALLOW,
                reasonCode,
                POLICY_VERSION,
                null,
                null));
    }

    private boolean isActiveInOrg(UUID appUserId, UUID organizationId) {
        return appUserId != null
                && users.findById(appUserId)
                        .map(user -> user.isActive()
                                && user.getOrganizationId().equals(organizationId))
                        .orElse(false);
    }

    private static Duration requireInterval(Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new BusinessValidationException(
                    "connection.crawl-interval-invalid",
                    "The content crawl interval must be positive");
        }
        return interval;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(
                    "connection.identifier-required",
                    "connection " + field + " is required");
        }
        return value.trim();
    }

    private static String requireSourceText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(
                    "source-connection.identifier-required",
                    "A " + field + " is required");
        }
        return value.trim();
    }

    private static final Comparator<SourceConnectionView> CONNECTION_ORDER =
            Comparator.comparing(SourceConnectionView::sourceSystem)
                    .thenComparing(SourceConnectionView::sourceConnectionKey);

    private record ConnectionKey(String sourceSystem, String sourceConnectionKey) {

        static ConnectionKey ofPrincipal(SourceConnectionPrincipalSummary principal) {
            return new ConnectionKey(principal.sourceSystem(), principal.sourceConnectionKey());
        }

        static ConnectionKey ofConnection(SourceConnection connection) {
            return new ConnectionKey(
                    connection.getSourceSystem(), connection.getSourceConnectionKey());
        }
    }

    private static final class ConnectionTally {

        private int userCount;
        private int mappedUserCount;
        private int groupCount;
        private Instant lastSeenAt;

        void observe(SourceConnectionPrincipalSummary principal) {
            if (principal.kind() == SourcePrincipalKind.SOURCE_GROUP) {
                groupCount++;
            } else {
                userCount++;
                if (principal.mapped()) {
                    mappedUserCount++;
                }
            }
            if (lastSeenAt == null || principal.lastSeenAt().isAfter(lastSeenAt)) {
                lastSeenAt = principal.lastSeenAt();
            }
        }

        SourceConnectionView toView(ConnectionKey key, SourceConnection decision) {
            return new SourceConnectionView(
                    key.sourceSystem(),
                    key.sourceConnectionKey(),
                    decision == null
                            ? SourceIdentityTrust.UNTRUSTED
                            : decision.getIdentityTrust(),
                    decision == null ? null : decision.getTrustDecidedByUserId(),
                    decision == null ? null : decision.getTrustDecidedAt(),
                    userCount,
                    mappedUserCount,
                    groupCount,
                    lastSeenAt);
        }
    }
}
