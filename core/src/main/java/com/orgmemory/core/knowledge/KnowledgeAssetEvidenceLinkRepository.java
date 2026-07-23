package com.orgmemory.core.knowledge;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface KnowledgeAssetEvidenceLinkRepository
        extends JpaRepository<KnowledgeAssetEvidenceLink, UUID> {
}
