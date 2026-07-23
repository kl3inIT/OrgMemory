package com.orgmemory.core.knowledge;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * An administrator's standing decision about one source connection. Connections
 * themselves are discovered from observed principals; a row exists here only once
 * somebody has ruled on how much its identities may be trusted.
 */
@Entity
@Table(name = "source_connections")
class SourceConnection extends BaseEntity {

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

    @Column(name = "channel_filter", nullable = false, length = 2048)
    private String channelFilter;

    @Column(name = "content_crawl_interval_seconds", nullable = false)
    private int contentCrawlIntervalSeconds;

    @Column(name = "max_threads_per_channel", nullable = false)
    private int maxThreadsPerChannel;

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
        this.channelFilter = "";
        this.contentCrawlIntervalSeconds = 3600;
        this.maxThreadsPerChannel = 500;
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
            String channelFilter,
            int contentCrawlIntervalSeconds,
            int maxThreadsPerChannel,
            UUID configuredByUserId,
            Instant configuredAt) {
        if (crawlEnabled && (knowledgeSpaceId == null || actorUserId == null)) {
            throw new IllegalArgumentException(
                    "A crawl needs a Knowledge Space to publish into and a user to publish as");
        }
        if (contentCrawlIntervalSeconds <= 0 || maxThreadsPerChannel <= 0) {
            throw new IllegalArgumentException("Crawl bounds must be positive");
        }
        this.crawlEnabled = crawlEnabled;
        this.knowledgeSpaceId = knowledgeSpaceId;
        this.actorUserId = actorUserId;
        this.channelFilter = channelFilter == null ? "" : channelFilter.strip();
        this.contentCrawlIntervalSeconds = contentCrawlIntervalSeconds;
        this.maxThreadsPerChannel = maxThreadsPerChannel;
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

    String getChannelFilter() {
        return channelFilter;
    }

    int getContentCrawlIntervalSeconds() {
        return contentCrawlIntervalSeconds;
    }

    int getMaxThreadsPerChannel() {
        return maxThreadsPerChannel;
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
