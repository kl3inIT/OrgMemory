package com.orgmemory.core.assetregistry.authorization;

import com.orgmemory.core.assetregistry.kernel.AssetAuthorizationBatch;
import com.orgmemory.core.assetregistry.kernel.AssetAuthorizationProjectionQueue;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetAuthorizationConvergenceService {

    private final AssetAuthorizationProjectionQueue queue;
    private final AssetAuthorizationProjectionService projection;

    AssetAuthorizationConvergenceService(
            AssetAuthorizationProjectionQueue queue,
            AssetAuthorizationProjectionService projection) {
        this.queue = queue;
        this.projection = projection;
    }

    @Transactional(propagation = Propagation.NEVER)
    public AssetAuthorizationConvergenceReport reconcile(int limit) {
        List<AssetAuthorizationBatch> candidates =
                queue.claimPending(limit);
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
