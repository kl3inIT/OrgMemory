package com.orgmemory.core.assetregistry.kernel;

import com.orgmemory.core.assetregistry.api.AssetRole;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface AssetRoleAssignmentRepository extends JpaRepository<AssetRoleAssignment, UUID> {

    Optional<AssetRoleAssignment>
            findByAssetIdAndPrincipalTypeAndPrincipalIdAndRoleAndValidUntilIsNull(
                    UUID assetId,
                    String principalType,
                    String principalId,
                    AssetRole role);

    List<AssetRoleAssignment> findByAssetIdOrderByValidFromAsc(UUID assetId);

    List<AssetRoleAssignment> findByAssetIdAndValidUntilIsNull(UUID assetId);

    @Query("""
            select assignment.assetId
            from AssetRoleAssignment assignment
            where assignment.organizationId = :organizationId
              and assignment.principalType = 'user'
              and assignment.principalId = :principalId
              and assignment.role = :role
              and assignment.validFrom <= :viewedAt
              and (
                    assignment.validUntil is null
                    or assignment.validUntil > :viewedAt
              )
            """)
    List<UUID> findActiveAssetIdsForUserRole(
            @Param("organizationId") UUID organizationId,
            @Param("principalId") String principalId,
            @Param("role") AssetRole role,
            @Param("viewedAt") Instant viewedAt);
}
