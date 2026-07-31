package com.orgmemory.core.knowledge.acl;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceGroupMembershipMemberRepository
        extends JpaRepository<SourceGroupMembershipMember, UUID> {

    List<SourceGroupMembershipMember> findByMembershipSnapshotId(UUID membershipSnapshotId);
}
