package com.orgmemory.core.ai;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ai_assistant_model_activations")
class AiAssistantModelActivation extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "gateway_profile_id", nullable = false, updatable = false)
    private UUID gatewayProfileId;

    @Column(name = "model_id", nullable = false, length = 200, updatable = false)
    private String modelId;

    @Column(name = "display_name", nullable = false, length = 200, updatable = false)
    private String displayName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "enabled_by_user_id", nullable = false, updatable = false)
    private UUID enabledByUserId;

    @Column(name = "disabled_by_user_id")
    private UUID disabledByUserId;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    protected AiAssistantModelActivation() {
    }

    AiAssistantModelActivation(
            UUID organizationId,
            UUID gatewayProfileId,
            String modelId,
            String displayName,
            UUID enabledByUserId) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.gatewayProfileId = Objects.requireNonNull(gatewayProfileId, "gatewayProfileId");
        this.modelId = Objects.requireNonNull(modelId, "modelId");
        this.displayName = Objects.requireNonNull(displayName, "displayName");
        this.enabled = true;
        this.enabledByUserId = Objects.requireNonNull(enabledByUserId, "enabledByUserId");
    }

    void disable(UUID actorUserId, Instant timestamp) {
        if (!enabled) {
            return;
        }
        enabled = false;
        disabledByUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        disabledAt = Objects.requireNonNull(timestamp, "timestamp");
    }

    AiAssistantModelActivationView view() {
        return new AiAssistantModelActivationView(
                getId(),
                organizationId,
                gatewayProfileId,
                modelId,
                displayName,
                enabled,
                getVersion());
    }

    UUID organizationId() {
        return organizationId;
    }

    UUID gatewayProfileId() {
        return gatewayProfileId;
    }

    String modelId() {
        return modelId;
    }

    boolean enabled() {
        return enabled;
    }
}
