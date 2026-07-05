package com.orgmemory.core.capability;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapabilityAssetService {

    private final CapabilityAssetRepository assets;
    private final AssetVersionRepository versions;
    private final AssetUsageEventRepository usageEvents;
    private final AssetApprovalEventRepository approvalEvents;

    public CapabilityAssetService(CapabilityAssetRepository assets, AssetVersionRepository versions,
            AssetUsageEventRepository usageEvents, AssetApprovalEventRepository approvalEvents) {
        this.assets = assets;
        this.versions = versions;
        this.usageEvents = usageEvents;
        this.approvalEvents = approvalEvents;
    }

    @Transactional
    public CapabilityAsset create(CreateCapabilityAssetCommand command) {
        CapabilityAsset asset = assets.save(new CapabilityAsset(command));
        AssetVersion version = versions.save(new AssetVersion(asset.getId(), 1, command));
        asset.setCurrentVersionId(version.getId());
        return assets.save(asset);
    }

    @Transactional(readOnly = true)
    public CapabilityAsset get(UUID id) {
        return assets.findById(id).orElseThrow(() -> new CapabilityAssetNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<CapabilityAsset> search(AssetStatus status, AssetType assetType, String query) {
        List<CapabilityAsset> candidates = status == null
                ? assets.findAllByOrderByUpdatedAtDesc()
                : assets.findByStatusOrderByUpdatedAtDesc(status);
        if (query == null || query.isBlank()) {
            return filterByAssetType(candidates, assetType);
        }
        String normalized = query.toLowerCase(Locale.ROOT);
        return filterByAssetType(candidates, assetType).stream()
                .filter(asset -> contains(asset.getTitle(), normalized)
                        || contains(asset.getSummary(), normalized)
                        || asset.getAssetType().name().toLowerCase(Locale.ROOT).contains(normalized)
                        || contains(asset.getUseCase(), normalized)
                        || contains(asset.getBusinessProcess(), normalized)
                        || contains(asset.getAiTool(), normalized)
                        || contains(asset.getTagNames(), normalized))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AssetVersion> versions(UUID assetId) {
        get(assetId);
        return versions.findByAssetIdOrderByVersionNumberDesc(assetId);
    }

    @Transactional(readOnly = true)
    public long usageCount(UUID assetId) {
        get(assetId);
        return usageEvents.countByAssetId(assetId);
    }

    @Transactional
    public CapabilityAsset submitForReview(UUID assetId, UUID reviewerUserId, String comment) {
        CapabilityAsset asset = get(assetId);
        asset.submitForReview();
        approvalEvents.save(new AssetApprovalEvent(assetId, reviewerUserId, ApprovalAction.SUBMITTED, comment));
        return asset;
    }

    @Transactional
    public CapabilityAsset approve(UUID assetId, UUID reviewerUserId, String comment) {
        CapabilityAsset asset = get(assetId);
        asset.approve();
        approvalEvents.save(new AssetApprovalEvent(assetId, reviewerUserId, ApprovalAction.APPROVED, comment));
        return asset;
    }

    @Transactional
    public CapabilityAsset reject(UUID assetId, UUID reviewerUserId, String comment) {
        CapabilityAsset asset = get(assetId);
        asset.reject();
        approvalEvents.save(new AssetApprovalEvent(assetId, reviewerUserId, ApprovalAction.REJECTED, comment));
        return asset;
    }

    @Transactional
    public CapabilityAsset deprecate(UUID assetId, UUID reviewerUserId, String comment) {
        CapabilityAsset asset = get(assetId);
        asset.deprecate();
        approvalEvents.save(new AssetApprovalEvent(assetId, reviewerUserId, ApprovalAction.DEPRECATED, comment));
        return asset;
    }

    @Transactional
    public CapabilityAsset assignBackupOwner(UUID assetId, UUID backupOwnerUserId, UUID reviewerUserId, String comment) {
        CapabilityAsset asset = get(assetId);
        asset.assignBackupOwner(backupOwnerUserId);
        approvalEvents.save(new AssetApprovalEvent(assetId, reviewerUserId, ApprovalAction.SUBMITTED, comment));
        return asset;
    }

    @Transactional
    public long recordUsage(UUID assetId, UUID userId, UsageEventType eventType, String metadataJson) {
        get(assetId);
        usageEvents.save(new AssetUsageEvent(assetId, userId, eventType, metadataJson));
        return usageEvents.countByAssetId(assetId);
    }

    private static boolean contains(String value, String normalizedQuery) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalizedQuery);
    }

    private static List<CapabilityAsset> filterByAssetType(List<CapabilityAsset> assets, AssetType assetType) {
        if (assetType == null) {
            return assets;
        }
        return assets.stream()
                .filter(asset -> asset.getAssetType() == assetType)
                .toList();
    }
}
