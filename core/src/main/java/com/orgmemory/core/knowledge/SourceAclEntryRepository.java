package com.orgmemory.core.knowledge;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceAclEntryRepository extends JpaRepository<SourceAclEntry, UUID> {

    List<SourceAclEntry> findBySourceAclSnapshotId(UUID sourceAclSnapshotId);
}
