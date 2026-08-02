package com.orgmemory.core.ai;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_route_overrides")
class AiRouteOverride extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private AiWorkload workload;

    @Column(name = "gateway_profile_id", nullable = false)
    private UUID gatewayProfileId;

    @Column(name = "model_id", nullable = false, length = 200)
    private String modelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "openai_reasoning_effort", length = 16)
    private OpenAiReasoningEffort openAiReasoningEffort;

    @Column(name = "set_by_user_id", nullable = false)
    private UUID setByUserId;

    @Column(name = "set_at", nullable = false)
    private Instant setAt;

    protected AiRouteOverride() {
    }

    AiRouteOverride(
            UUID organizationId,
            AiWorkload workload,
            UUID gatewayProfileId,
            String modelId,
            OpenAiReasoningEffort openAiReasoningEffort,
            UUID setByUserId,
            Instant setAt) {
        super(UUID.randomUUID());
        this.organizationId = organizationId;
        this.workload = workload;
        replace(
                gatewayProfileId,
                modelId,
                openAiReasoningEffort,
                setByUserId,
                setAt);
    }

    AiRouteOverride(
            UUID organizationId,
            AiWorkload workload,
            UUID gatewayProfileId,
            String modelId,
            UUID setByUserId,
            Instant setAt) {
        this(
                organizationId,
                workload,
                gatewayProfileId,
                modelId,
                null,
                setByUserId,
                setAt);
    }

    void replace(
            UUID gatewayProfileId,
            String modelId,
            OpenAiReasoningEffort openAiReasoningEffort,
            UUID setByUserId,
            Instant setAt) {
        this.gatewayProfileId = gatewayProfileId;
        this.modelId = modelId;
        this.openAiReasoningEffort = openAiReasoningEffort;
        this.setByUserId = setByUserId;
        this.setAt = setAt;
    }

    AiWorkload workload() {
        return workload;
    }

    UUID gatewayProfileId() {
        return gatewayProfileId;
    }

    String modelId() {
        return modelId;
    }

    OpenAiReasoningEffort openAiReasoningEffort() {
        return openAiReasoningEffort;
    }

    UUID setByUserId() {
        return setByUserId;
    }

    Instant setAt() {
        return setAt;
    }
}
