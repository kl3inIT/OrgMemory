package com.orgmemory.core.assetregistry.skill;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "asset_skill_activations")
class AssetSkillActivation extends BaseEntity {

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "asset_id", nullable = false, updatable = false)
    private UUID assetId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false)
    private boolean enabled;

    protected AssetSkillActivation() {
    }

    AssetSkillActivation(UUID organizationId, UUID assetId, UUID userId, boolean enabled) {
        super(UUID.randomUUID());
        this.organizationId = java.util.Objects.requireNonNull(organizationId, "organizationId");
        this.assetId = java.util.Objects.requireNonNull(assetId, "assetId");
        this.userId = java.util.Objects.requireNonNull(userId, "userId");
        this.enabled = enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    boolean isEnabled() {
        return enabled;
    }
}
