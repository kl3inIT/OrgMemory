package com.orgmemory.core.assetregistry;

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
@Table(name = "asset_audit_events")
class AssetAuditEvent extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @Column(name = "subject_type", nullable = false, length = 64, updatable = false)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private UUID subjectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "decision_context", nullable = false, updatable = false)
    private String decisionContext;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AssetAuditEvent() {
    }

    AssetAuditEvent(
            UUID organizationId,
            UUID assetId,
            UUID actorUserId,
            String eventType,
            String subjectType,
            UUID subjectId,
            String decisionContext,
            Instant occurredAt) {
        super(UUID.randomUUID());
        this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = Objects.requireNonNull(assetId, "assetId");
        this.actorUserId = Objects.requireNonNull(actorUserId, "actorUserId");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.subjectType = Objects.requireNonNull(subjectType, "subjectType");
        this.subjectId = Objects.requireNonNull(subjectId, "subjectId");
        this.decisionContext = Objects.requireNonNull(decisionContext, "decisionContext");
        this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
