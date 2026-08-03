package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.skillstorage.SkillPackageStoragePort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class SkillPackageSupersessionCleanupCoordinator {

    private final SkillPackageSupersessionRepository supersessions;
    private final AssetPayloadReferenceRepository references;
    private final SkillPackageStoragePort storage;

    SkillPackageSupersessionCleanupCoordinator(
            SkillPackageSupersessionRepository supersessions,
            AssetPayloadReferenceRepository references,
            SkillPackageStoragePort storage) {
        this.supersessions = supersessions;
        this.references = references;
        this.storage = storage;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    SkillPackageCleanupOutcome cleanup(UUID supersessionId) {
        SkillPackageSupersession item = supersessions.findForUpdate(supersessionId)
                .orElse(null);
        if (item == null) {
            return SkillPackageCleanupOutcome.ALREADY_RESOLVED;
        }
        if (references.existsByOrganizationIdAndReferenceValue(
                item.getOrganizationId(), item.getSupersededReferenceValue())) {
            supersessions.delete(item);
            return SkillPackageCleanupOutcome.RETAINED_BY_IMMUTABLE_REFERENCE;
        }
        try {
            storage.delete(item.getSupersededReferenceValue());
            supersessions.delete(item);
            return SkillPackageCleanupOutcome.DELETED;
        } catch (RuntimeException failure) {
            item.recordFailure(failure, Instant.now());
            supersessions.save(item);
            return SkillPackageCleanupOutcome.RETRY_SCHEDULED;
        }
    }
}
