package com.orgmemory.core.knowledge;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SourceMembershipSyncRunRepository
        extends JpaRepository<SourceMembershipSyncRun, UUID> {
}
