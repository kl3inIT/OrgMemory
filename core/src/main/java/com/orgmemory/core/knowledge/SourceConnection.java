package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An administrator's standing decision about one source connection. Connections
 * themselves are discovered from observed principals; a row exists here only once
 * somebody has ruled on how much its identities may be trusted.
 */
@Entity
@Table(name = "source_connections")
class SourceConnection extends BaseEntity {

    private static final String EMPTY_CONFIG = "{}";

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "source_system", nullable = false, length = 64, updatable = false)
    private String sourceSystem;

    @Column(name = "source_connection_key", nullable = false, length = 128, updatable = false)
    private String sourceConnectionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "identity_trust", nullable = false, length = 32)
    private SourceIdentityTrust identityTrust;

    @Column(name = "trust_decided_by_user_id")
    private UUID trustDecidedByUserId;

    @Column(name = "trust_decided_at")
    private Instant trustDecidedAt;

    @Column(name = "crawl_enabled", nullable = false)
    private boolean crawlEnabled;

    @Column(name = "knowledge_space_id")
    private UUID knowledgeSpaceId;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "content_crawl_interval_seconds", nullable = false)
    private int contentCrawlIntervalSeconds;

    /**
     * Settings only this source system understands. Held as the JSON document it arrived as
     * and never parsed here: the ledger has no basis for an opinion about a channel filter or
     * a folder filter, and giving it one is what made adding a source a migration.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_config", nullable = false)
    private String sourceConfig;

    @Column(name = "crawl_configured_by_user_id")
    private UUID crawlConfiguredByUserId;

    @Column(name = "crawl_configured_at")
    private Instant crawlConfiguredAt;

    protected SourceConnection() {
    }

    SourceConnection(UUID organizationId, String sourceSystem, String sourceConnectionKey) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.sourceSystem = sourceSystem;
        this.sourceConnectionKey = sourceConnectionKey;
        this.identityTrust = SourceIdentityTrust.UNTRUSTED;
        this.crawlEnabled = false;
        this.contentCrawlIntervalSeconds = 3600;
        this.sourceConfig = EMPTY_CONFIG;
    }

    /**
     * Records how this connection should be crawled. Enabling without a target Space or an actor
     * is refused here as well as by the database, because the alternative is a crawl that fails
     * once per object long after the mistake was made.
     */
    void configureCrawl(
            boolean crawlEnabled,
            UUID knowledgeSpaceId,
            UUID actorUserId,
            String sourceConfig,
            int contentCrawlIntervalSeconds,
            UUID configuredByUserId,
            Instant configuredAt) {
        if (crawlEnabled && (knowledgeSpaceId == null || actorUserId == null)) {
            throw new IllegalArgumentException(
                    "A crawl needs a Knowledge Space to publish into and a user to publish as");
        }
        if (contentCrawlIntervalSeconds <= 0) {
            throw new IllegalArgumentException("The content crawl interval must be positive");
        }
        this.crawlEnabled = crawlEnabled;
        this.knowledgeSpaceId = knowledgeSpaceId;
        this.actorUserId = actorUserId;
        this.sourceConfig = sourceConfig == null || sourceConfig.isBlank() ? EMPTY_CONFIG : sourceConfig;
        this.contentCrawlIntervalSeconds = contentCrawlIntervalSeconds;
        this.crawlConfiguredByUserId = configuredByUserId;
        this.crawlConfiguredAt = configuredAt;
    }

    boolean isCrawlEnabled() {
        return crawlEnabled;
    }

    UUID getKnowledgeSpaceId() {
        return knowledgeSpaceId;
    }

    UUID getActorUserId() {
        return actorUserId;
    }

    String getSourceConfig() {
        return sourceConfig;
    }

    int getContentCrawlIntervalSeconds() {
        return contentCrawlIntervalSeconds;
    }

    UUID getCrawlConfiguredByUserId() {
        return crawlConfiguredByUserId;
    }

    Instant getCrawlConfiguredAt() {
        return crawlConfiguredAt;
    }

    void decideTrust(SourceIdentityTrust identityTrust, UUID decidedByUserId, Instant decidedAt) {
        this.identityTrust = identityTrust;
        if (identityTrust == SourceIdentityTrust.UNTRUSTED) {
            this.trustDecidedByUserId = null;
            this.trustDecidedAt = null;
            return;
        }
        this.trustDecidedByUserId = decidedByUserId;
        this.trustDecidedAt = decidedAt;
    }

    UUID getOrganizationId() {
        return organizationId;
    }

    String getSourceSystem() {
        return sourceSystem;
    }

    String getSourceConnectionKey() {
        return sourceConnectionKey;
    }

    SourceIdentityTrust getIdentityTrust() {
        return identityTrust;
    }

    UUID getTrustDecidedByUserId() {
        return trustDecidedByUserId;
    }

    Instant getTrustDecidedAt() {
        return trustDecidedAt;
    }
}
