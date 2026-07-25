package com.orgmemory.core.assetregistry;

import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AssetAuthorizationConvergenceService {

    private final AssetAuthorizationCoordinator coordinator;
    private final AssetAuthorizationProjectionService projection;

    AssetAuthorizationConvergenceService(
            AssetAuthorizationCoordinator coordinator,
            AssetAuthorizationProjectionService projection) {
        this.coordinator = coordinator;
        this.projection = projection;
    }

    public AssetAuthorizationConvergenceReport reconcile(int limit) {
        List<AssetAuthorizationBatch> candidates =
                coordinator.claimPendingBatches(limit);
        int applied = 0;
        int failed = 0;
        for (AssetAuthorizationBatch candidate : candidates) {
            try {
                projection.project(candidate);
                applied++;
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        return new AssetAuthorizationConvergenceReport(candidates.size(), applied, failed);
    }
}
