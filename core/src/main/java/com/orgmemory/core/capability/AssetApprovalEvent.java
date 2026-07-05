package com.orgmemory.core.capability;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "asset_approval_events")
public class AssetApprovalEvent extends BaseEntity {

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "reviewer_user_id")
    private UUID reviewerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalAction action;

    @Column(columnDefinition = "text")
    private String comment;

    protected AssetApprovalEvent() {
    }

    public AssetApprovalEvent(UUID assetId, UUID reviewerUserId, ApprovalAction action, String comment) {
        super(UUID.randomUUID());
        this.assetId = assetId;
        this.reviewerUserId = reviewerUserId;
        this.action = action;
        this.comment = comment;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public ApprovalAction getAction() {
        return action;
    }
}
