package com.orgmemory.core.assetregistry.workinstruction;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "work_instruction_acknowledgements")
class WorkInstructionAcknowledgement extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "release_id", nullable = false, updatable = false)
    private UUID releaseId;

    @Column(name = "release_digest", nullable = false, length = 64, updatable = false)
    private String releaseDigest;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(name = "acknowledged_at", nullable = false, updatable = false)
    private Instant acknowledgedAt;

    protected WorkInstructionAcknowledgement() {
    }

    Instant acknowledgedAt() {
        return acknowledgedAt;
    }
}
