package com.orgmemory.core.assetregistry.prompt;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface PromptRunRepository extends JpaRepository<PromptRun, UUID> {
}
