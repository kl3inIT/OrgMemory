package com.orgmemory.core.knowledge.acl;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceGroupMembershipSnapshotSealRepository
        extends JpaRepository<SourceGroupMembershipSnapshotSeal, UUID> {

    Optional<SourceGroupMembershipSnapshotSeal> findByMembershipSnapshotId(
            UUID membershipSnapshotId);
}
