package com.orgmemory.core.capability;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "asset_usage_events")
public class AssetUsageEvent extends BaseEntity {

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private UsageEventType eventType;

    @Column(name = "metadata_json", columnDefinition = "text")
    private String metadataJson;

    protected AssetUsageEvent() {
    }

    public AssetUsageEvent(UUID assetId, UUID userId, UsageEventType eventType, String metadataJson) {
        super(UUID.randomUUID());
        this.assetId = assetId;
        this.userId = userId;
        this.eventType = eventType;
        this.metadataJson = metadataJson;
    }

    public UUID getAssetId() {
        return assetId;
    }

    public UsageEventType getEventType() {
        return eventType;
    }
}
