package com.orgmemory.core.knowledge.acl;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceAclEntryRepository extends JpaRepository<SourceAclEntry, UUID> {

    List<SourceAclEntry> findBySourceAclSnapshotId(UUID sourceAclSnapshotId);
}
