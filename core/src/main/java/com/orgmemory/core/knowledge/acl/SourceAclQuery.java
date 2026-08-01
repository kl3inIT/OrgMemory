package com.orgmemory.core.knowledge.acl;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read-only ACL boundary for consumers that do not own ACL persistence. */
@Service
@Transactional(readOnly = true)
public class SourceAclQuery {

    private final SourceAclSnapshotRepository snapshots;

    SourceAclQuery(SourceAclSnapshotRepository snapshots) {
        this.snapshots = snapshots;
    }

    public Optional<SourceAclSnapshotRef> findSnapshot(
            UUID organizationId,
            UUID snapshotId) {
        return snapshots.findByIdAndOrganizationId(snapshotId, organizationId)
                .map(SourceAclSnapshotRef::from);
    }

    public List<KnowledgeSpaceAclGenerationRef> maximumCurrentAclGenerations(
            UUID organizationId,
            Collection<UUID> assetIds) {
        return snapshots.maximumCurrentAclGenerations(organizationId, assetIds).stream()
                .map(generation -> new KnowledgeSpaceAclGenerationRef(
                        generation.getKnowledgeSpaceId(),
                        generation.getAclGeneration()))
                .toList();
    }
}
