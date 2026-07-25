package com.orgmemory.core.assetregistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AssetRoleAssignmentRepository extends JpaRepository<AssetRoleAssignment, UUID> {

    Optional<AssetRoleAssignment>
            findByAssetIdAndPrincipalTypeAndPrincipalIdAndRoleAndValidUntilIsNull(
                    UUID assetId,
                    String principalType,
                    String principalId,
                    AssetRole role);

    List<AssetRoleAssignment> findByAssetIdOrderByValidFromAsc(UUID assetId);
}
