package com.orgmemory.core.assetregistry;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "pack_progress")
class PackProgress extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "assignment_id", nullable = false, updatable = false)
    private UUID assignmentId;

    @Column(name = "item_key", nullable = false, length = 64, updatable = false)
    private String itemKey;

    @Column(nullable = false)
    private boolean completed;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected PackProgress() {
    }

    String getItemKey() {
        return itemKey;
    }

    boolean isCompleted() {
        return completed;
    }

    Instant getCompletedAt() {
        return completedAt;
    }
}
