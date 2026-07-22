package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceAclGroupMemberRepository extends JpaRepository<SourceAclGroupMember, UUID> {

    List<SourceAclGroupMember> findBySourceAclSnapshotId(UUID sourceAclSnapshotId);
}
