package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.RelationshipTuple;
import com.orgmemory.core.authorization.RelationshipTupleReconciliationPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Repairs the PostgreSQL/OpenFGA projection boundary without treating OpenFGA as
 * canonical content storage. Model rotation rewrites idempotent direct tuples;
 * orphan cleanup deletes only OrgMemory-owned UUID asset tuples.
 */
/**
 * Always a bean, because both ports always exist: the OpenFGA adapter contributes them when it
 * is configured and a fail-closed stand-in does when it is not. Making this conditional on them
 * instead resolved against whichever packages the component scan had reached, so a fully
 * configured deployment could silently lose convergence — and a scheduler that read the same
 * condition a moment later would disagree and refuse to start.
 */
@Service
public class KnowledgeAuthorizationConvergenceService {

    private static final String ASSET_PREFIX = "knowledge_asset:";
    private static final Set<String> MANAGED_RELATIONS = Set.of("owner", "space");

    private final KnowledgeAssetPublicationCoordinator publications;
    private final RelationshipTupleWritePort tupleWriter;
    private final RelationshipTupleReconciliationPort tupleReconciliation;

    KnowledgeAuthorizationConvergenceService(
            KnowledgeAssetPublicationCoordinator publications,
            RelationshipTupleWritePort tupleWriter,
            RelationshipTupleReconciliationPort tupleReconciliation) {
        this.publications = publications;
        this.tupleWriter = tupleWriter;
        this.tupleReconciliation = tupleReconciliation;
    }

    public KnowledgeAuthorizationConvergenceReport reconcile(
            int modelBatchSize,
            int tuplePageSize,
            int maximumTuplePages) {
        if (modelBatchSize <= 0 || tuplePageSize <= 0 || maximumTuplePages <= 0) {
            throw new IllegalArgumentException("Convergence limits must be positive");
        }

        String policyVersion = tupleReconciliation.policyVersion();
        List<KnowledgeAssetPublicationState> drift =
                publications.findAuthorizationModelDrift(policyVersion, modelBatchSize);
        int repaired = repairModelDrift(drift);
        if (repaired < drift.size()) {
            return report(drift.size(), repaired, 0, 0, false, "OPENFGA_MODEL_REPAIR_FAILED");
        }

        int scanned = 0;
        var orphanBatches = new ArrayList<List<RelationshipTuple>>();
        String continuationToken = null;
        for (int pageNumber = 0; pageNumber < maximumTuplePages; pageNumber++) {
            var page = tupleReconciliation.read(tuplePageSize, continuationToken);
            if (!page.resolved()) {
                return report(
                        drift.size(), repaired, scanned, 0, false, page.reasonCode());
            }
            scanned += page.tuples().size();
            List<RelationshipTuple> orphans = orphanedManagedTuples(page.tuples());
            if (!orphans.isEmpty()) {
                orphanBatches.add(orphans);
            }
            if (!page.hasNextPage()) {
                return deleteOrphans(drift.size(), repaired, scanned, orphanBatches);
            }
            continuationToken = page.continuationToken();
        }
        return report(
                drift.size(),
                repaired,
                scanned,
                0,
                false,
                "OPENFGA_SCAN_PAGE_LIMIT");
    }

    private KnowledgeAuthorizationConvergenceReport deleteOrphans(
            int driftDetected,
            int driftRepaired,
            int tuplesScanned,
            List<List<RelationshipTuple>> orphanBatches) {
        int deleted = 0;
        for (List<RelationshipTuple> orphans : orphanBatches) {
            var result = tupleReconciliation.delete(new RelationshipTupleWriteRequest(orphans));
            if (!result.applied()) {
                return report(
                        driftDetected,
                        driftRepaired,
                        tuplesScanned,
                        deleted,
                        false,
                        result.reasonCode());
            }
            deleted += orphans.size();
        }
        return report(
                driftDetected,
                driftRepaired,
                tuplesScanned,
                deleted,
                true,
                null);
    }

    private int repairModelDrift(List<KnowledgeAssetPublicationState> drift) {
        if (drift.isEmpty()) {
            return 0;
        }
        List<RelationshipTuple> tuples = drift.stream()
                .flatMap(publication -> publicationTuples(publication).stream())
                .toList();
        var result = tupleWriter.write(new RelationshipTupleWriteRequest(tuples));
        if (!result.applied()) {
            return 0;
        }
        for (KnowledgeAssetPublicationState publication : drift) {
            publications.recordAuthorizationModel(
                    publication.organizationId(),
                    publication.publicationId(),
                    result.policyVersion());
        }
        return drift.size();
    }

    private List<RelationshipTuple> orphanedManagedTuples(List<RelationshipTuple> tuples) {
        var candidates = new ArrayList<RelationshipTuple>();
        var candidateIds = new HashSet<UUID>();
        for (RelationshipTuple tuple : tuples) {
            UUID assetId = managedAssetId(tuple);
            if (assetId != null) {
                candidates.add(tuple);
                candidateIds.add(assetId);
            }
        }
        Set<UUID> existing = publications.existingAssetIds(candidateIds);
        return candidates.stream()
                .filter(tuple -> !existing.contains(Objects.requireNonNull(managedAssetId(tuple))))
                .toList();
    }

    private static UUID managedAssetId(RelationshipTuple tuple) {
        if (!MANAGED_RELATIONS.contains(tuple.relation())
                || !tuple.object().startsWith(ASSET_PREFIX)) {
            return null;
        }
        try {
            return UUID.fromString(tuple.object().substring(ASSET_PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static List<RelationshipTuple> publicationTuples(
            KnowledgeAssetPublicationState publication) {
        return List.of(
                RelationshipTuple.of(
                        "knowledge_space:" + publication.knowledgeSpaceId(),
                        "space",
                        ASSET_PREFIX + publication.knowledgeAssetId()),
                RelationshipTuple.of(
                        "user:" + publication.ownerUserId(),
                        "owner",
                        ASSET_PREFIX + publication.knowledgeAssetId()));
    }

    private static KnowledgeAuthorizationConvergenceReport report(
            int detected,
            int repaired,
            int scanned,
            int deleted,
            boolean complete,
            String reasonCode) {
        return new KnowledgeAuthorizationConvergenceReport(
                detected, repaired, scanned, deleted, complete, reasonCode);
    }
}
