package com.orgmemory.core.assistant;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "assistant_asset_traces")
class AssistantAssetTrace extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 48, updatable = false)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "release_refs", nullable = false, updatable = false)
    private String releaseRefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "knowledge_refs", nullable = false, updatable = false)
    private String knowledgeRefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "model_route", nullable = false, updatable = false)
    private String modelRoute;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_call", nullable = false, updatable = false)
    private String toolCall;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "authorization_context", nullable = false, updatable = false)
    private String authorizationContext;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sanitized_outcome", nullable = false, updatable = false)
    private String sanitizedOutcome;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AssistantAssetTrace() {
    }

    AssistantAssetTrace(
            UUID organizationId,
            UUID actorUserId,
            AssistantAssetAction action,
            String releaseRefs,
            String knowledgeRefs,
            String modelRoute,
            String toolCall,
            String authorizationContext,
            String sanitizedOutcome,
            Instant occurredAt) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.action = Objects.requireNonNull(action, "action").name();
        this.releaseRefs = Objects.requireNonNull(releaseRefs, "releaseRefs");
        this.knowledgeRefs = Objects.requireNonNull(knowledgeRefs, "knowledgeRefs");
        this.modelRoute = Objects.requireNonNull(modelRoute, "modelRoute");
        this.toolCall = Objects.requireNonNull(toolCall, "toolCall");
        this.authorizationContext =
                Objects.requireNonNull(authorizationContext, "authorizationContext");
        this.sanitizedOutcome =
                Objects.requireNonNull(sanitizedOutcome, "sanitizedOutcome");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
