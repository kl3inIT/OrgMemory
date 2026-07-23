package com.orgmemory.core.knowledge;

import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.secret.SecretCipher;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
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
 */
@Service
public class SourceConnectionAdminService {

    static final String POLICY_VERSION = "connection-admin-v1";

    private final SourceConnectionRepository connections;
    private final SourceConnectionCredentialRepository credentials;
    private final KnowledgeSpaceService knowledgeSpaces;
    private final SecretCipher cipher;
    private final PermissionAuditService audit;

    SourceConnectionAdminService(
            SourceConnectionRepository connections,
            SourceConnectionCredentialRepository credentials,
            KnowledgeSpaceService knowledgeSpaces,
            SecretCipher cipher,
            PermissionAuditService audit) {
        this.connections = connections;
        this.credentials = credentials;
        this.knowledgeSpaces = knowledgeSpaces;
        this.cipher = cipher;
        this.audit = audit;
    }

    /** The connection as an administrator sees it, or empty when none has been configured. */
    @Transactional(readOnly = true)
    public Optional<SourceConnectionConfigurationView> describe(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        return find(organizationId, sourceSystem, sourceConnectionKey).map(this::toView);
    }

    /**
     * Every connection of one source system in this organization. A connection appears here
     * once anybody has ruled on it, which includes connections a crawl discovered and nobody
     * has configured yet — those are precisely the ones an administrator needs to see.
     */
    @Transactional(readOnly = true)
    public List<SourceConnectionConfigurationView> list(UUID organizationId, String sourceSystem) {
        return connections
                .findByOrganizationIdAndSourceSystemOrderBySourceConnectionKeyAsc(
                        organizationId, requireText(sourceSystem, "sourceSystem"))
                .stream()
                .map(this::toView)
                .toList();
    }

    /**
     * Records how a connection should be crawled, creating the connection row if this is the
     * first thing anybody has said about it.
     */
    @Transactional
    public SourceConnectionConfigurationView configure(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            boolean crawlEnabled,
            UUID knowledgeSpaceId,
            UUID actorUserId,
            List<String> channels,
            Duration contentCrawlInterval,
            int maxThreadsPerChannel,
            UUID adminUserId) {
        String system = requireText(sourceSystem, "sourceSystem");
        String key = requireText(sourceConnectionKey, "sourceConnectionKey");
        if (knowledgeSpaceId != null) {
            knowledgeSpaces.requireInOrganization(organizationId, knowledgeSpaceId);
        }
        SourceConnection connection = find(organizationId, system, key)
                .orElseGet(() -> connections.save(new SourceConnection(organizationId, system, key)));
        connection.configureCrawl(
                crawlEnabled,
                knowledgeSpaceId,
                actorUserId,
                String.join(",", channels == null ? List.of() : channels),
                (int) requireInterval(contentCrawlInterval).toSeconds(),
                maxThreadsPerChannel,
                adminUserId,
                Instant.now());
        connections.save(connection);
        record(organizationId, adminUserId, system, key, "SOURCE_CONNECTION_CONFIGURE",
                crawlEnabled ? "CRAWL_ENABLED" : "CRAWL_DISABLED");
        return toView(connection);
    }

    /**
     * Stores a credential for the connection, replacing any previous one.
     *
     * <p>The audit event records that a credential was set and by whom; it does not record the
     * value, a prefix of it, or its length.
     */
    @Transactional
    public void setCredential(
            UUID organizationId, String sourceSystem, String sourceConnectionKey,
            SecretValue token, UUID adminUserId) {
        Objects.requireNonNull(token, "token");
        String system = requireText(sourceSystem, "sourceSystem");
        String key = requireText(sourceConnectionKey, "sourceConnectionKey");
        SourceConnection connection = find(organizationId, system, key)
                .orElseGet(() -> connections.save(new SourceConnection(organizationId, system, key)));
        Instant now = Instant.now();
        credentials.findByOrganizationIdAndSourceConnectionId(organizationId, connection.getId())
                .ifPresentOrElse(
                        existing -> {
                            existing.replaceWith(cipher.encrypt(token), adminUserId, now);
                            credentials.save(existing);
                        },
                        () -> credentials.save(new SourceConnectionCredential(
                                organizationId, connection.getId(), cipher.encrypt(token), adminUserId, now)));
        record(organizationId, adminUserId, system, key, "SOURCE_CONNECTION_CREDENTIAL", "CREDENTIAL_SET");
    }

    @Transactional
    public void forgetCredential(
            UUID organizationId, String sourceSystem, String sourceConnectionKey, UUID adminUserId) {
        String system = requireText(sourceSystem, "sourceSystem");
        String key = requireText(sourceConnectionKey, "sourceConnectionKey");
        find(organizationId, system, key).ifPresent(connection ->
                credentials.deleteByOrganizationIdAndSourceConnectionId(organizationId, connection.getId()));
        record(organizationId, adminUserId, system, key, "SOURCE_CONNECTION_CREDENTIAL", "CREDENTIAL_CLEARED");
    }

    /** The credential an adapter needs to authenticate. The only read path for a stored secret. */
    @Transactional(readOnly = true)
    public Optional<SecretValue> resolveCredential(
            UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        return find(organizationId, requireText(sourceSystem, "sourceSystem"),
                        requireText(sourceConnectionKey, "sourceConnectionKey"))
                .flatMap(connection -> credentials.findByOrganizationIdAndSourceConnectionId(
                        organizationId, connection.getId()))
                .map(credential -> cipher.decrypt(credential.stored()));
    }

    /** Every connection of one source system that an administrator has enabled, across tenants. */
    @Transactional(readOnly = true)
    public List<ConnectorCrawlConfiguration> enabledCrawls(String sourceSystem) {
        return connections.findBySourceSystemAndCrawlEnabledTrue(requireText(sourceSystem, "sourceSystem"))
                .stream()
                .map(connection -> new ConnectorCrawlConfiguration(
                        connection.getOrganizationId(),
                        connection.getSourceSystem(),
                        connection.getSourceConnectionKey(),
                        connection.getKnowledgeSpaceId(),
                        connection.getActorUserId(),
                        channelsOf(connection),
                        Duration.ofSeconds(connection.getContentCrawlIntervalSeconds()),
                        connection.getMaxThreadsPerChannel()))
                .toList();
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
                channelsOf(connection),
                connection.getContentCrawlIntervalSeconds(),
                connection.getMaxThreadsPerChannel(),
                credential.isPresent(),
                credential.map(SourceConnectionCredential::getSetByUserId).orElse(null),
                credential.map(SourceConnectionCredential::getSetAt).orElse(null),
                connection.getCrawlConfiguredByUserId(),
                connection.getCrawlConfiguredAt());
    }

    private static List<String> channelsOf(SourceConnection connection) {
        String filter = connection.getChannelFilter();
        if (filter == null || filter.isBlank()) {
            return List.of();
        }
        return Arrays.stream(filter.split(",")).map(String::strip).filter(name -> !name.isEmpty()).toList();
    }

    private void record(
            UUID organizationId, UUID adminUserId, String system, String key,
            String operation, String reasonCode) {
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

    private static Duration requireInterval(Duration interval) {
        if (interval == null || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("The content crawl interval must be positive");
        }
        return interval;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("connection " + field + " is required");
        }
        return value.trim();
    }
}
