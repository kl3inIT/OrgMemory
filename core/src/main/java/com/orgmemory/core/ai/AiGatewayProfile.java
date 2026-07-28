package com.orgmemory.core.ai;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "ai_gateway_profiles")
class AiGatewayProfile extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "gateway_key", nullable = false, length = 64, updatable = false)
    private String gatewayKey;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiGatewayPreset preset;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiGatewayCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiGatewayProtocol protocol;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(name = "request_timeout_seconds", nullable = false)
    private int requestTimeoutSeconds;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "runtime_revision", nullable = false)
    private long runtimeRevision;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private UUID createdByUserId;

    @Column(name = "updated_by_user_id", nullable = false)
    private UUID updatedByUserId;

    protected AiGatewayProfile() {
    }

    AiGatewayProfile(
            UUID organizationId,
            String gatewayKey,
            String displayName,
            AiGatewayPreset preset,
            AiGatewayCategory category,
            AiGatewayProtocol protocol,
            String baseUrl,
            int requestTimeoutSeconds,
            UUID adminUserId) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.gatewayKey = gatewayKey;
        this.displayName = displayName;
        this.preset = preset;
        this.category = category;
        this.protocol = protocol;
        this.baseUrl = baseUrl;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.enabled = true;
        this.runtimeRevision = 1;
        this.createdByUserId = adminUserId;
        this.updatedByUserId = adminUserId;
    }

    void update(
            String displayName,
            String baseUrl,
            int requestTimeoutSeconds,
            UUID adminUserId) {
        this.displayName = displayName;
        this.baseUrl = baseUrl;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.updatedByUserId = adminUserId;
        this.runtimeRevision++;
    }

    void touch(UUID adminUserId) {
        this.updatedByUserId = adminUserId;
        this.runtimeRevision++;
    }

    void disable(UUID adminUserId) {
        this.enabled = false;
        this.updatedByUserId = adminUserId;
        this.runtimeRevision++;
    }

    UUID organizationId() {
        return organizationId;
    }

    String gatewayKey() {
        return gatewayKey;
    }

    String displayName() {
        return displayName;
    }

    AiGatewayPreset preset() {
        return preset;
    }

    AiGatewayCategory category() {
        return category;
    }

    AiGatewayProtocol protocol() {
        return protocol;
    }

    String baseUrl() {
        return baseUrl;
    }

    int requestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    boolean enabled() {
        return enabled;
    }

    UUID createdByUserId() {
        return createdByUserId;
    }

    UUID updatedByUserId() {
        return updatedByUserId;
    }

    long cacheVersion() {
        return runtimeRevision;
    }
}
