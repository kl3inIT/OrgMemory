package com.orgmemory.core.knowledge;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceMembershipSyncRunRepository
        extends JpaRepository<SourceMembershipSyncRun, UUID> {
}
