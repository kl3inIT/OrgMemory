package com.orgmemory.core.capability;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "capability_assets")
public class CapabilityAsset extends BaseEntity {

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false)
    private AssetType assetType;

    @Column(name = "use_case")
    private String useCase;

    @Column(name = "business_process")
    private String businessProcess;

    @Column(name = "ai_tool")
    private String aiTool;

    @Column(name = "tag_names")
    private String tagNames;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "backup_owner_user_id")
    private UUID backupOwnerUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssetVisibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level")
    private RiskLevel riskLevel;

    @Column(name = "current_version_id")
    private UUID currentVersionId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    protected CapabilityAsset() {
    }

    public CapabilityAsset(CreateCapabilityAssetCommand command) {
        super(UUID.randomUUID());
        this.organizationId = command.organizationId();
        this.departmentId = command.departmentId();
        this.title = command.title();
        this.summary = command.summary();
        this.assetType = command.assetType() == null ? AssetType.WORKFLOW_AUTOMATION : command.assetType();
        this.useCase = command.useCase();
        this.businessProcess = command.businessProcess();
        this.aiTool = command.aiTool();
        this.tagNames = command.tagNames();
        this.ownerUserId = command.ownerUserId();
        this.backupOwnerUserId = command.backupOwnerUserId();
        this.status = AssetStatus.DRAFT;
        this.visibility = command.visibility();
        this.riskLevel = command.riskLevel();
        this.createdByUserId = command.createdByUserId();
    }

    public void setCurrentVersionId(UUID currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public void assignBackupOwner(UUID backupOwnerUserId) {
        this.backupOwnerUserId = backupOwnerUserId;
    }

    public void submitForReview() {
        this.status = AssetStatus.IN_REVIEW;
    }

    public void approve() {
        this.status = AssetStatus.APPROVED;
    }

    public void reject() {
        this.status = AssetStatus.REJECTED;
    }

    public void deprecate() {
        this.status = AssetStatus.DEPRECATED;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public AssetType getAssetType() {
        return assetType;
    }

    public String getUseCase() {
        return useCase;
    }

    public String getBusinessProcess() {
        return businessProcess;
    }

    public String getAiTool() {
        return aiTool;
    }

    public String getTagNames() {
        return tagNames;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public UUID getBackupOwnerUserId() {
        return backupOwnerUserId;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public AssetVisibility getVisibility() {
        return visibility;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public UUID getCurrentVersionId() {
        return currentVersionId;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }
}
