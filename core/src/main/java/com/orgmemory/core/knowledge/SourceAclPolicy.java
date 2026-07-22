package com.orgmemory.core.knowledge;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.AccessGate;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class SourceAclPolicy {

    private final SourceAclSnapshotRepository snapshots;
    private final SourceAclEntryRepository entries;
    private final SourceAclSnapshotSealRepository seals;

    SourceAclPolicy(
            SourceAclSnapshotRepository snapshots,
            SourceAclEntryRepository entries,
            SourceAclSnapshotSealRepository seals) {
        this.snapshots = snapshots;
        this.entries = entries;
        this.seals = seals;
    }

    SourceAclEvaluation evaluate(
            CurrentActor actor,
            UUID persistedDepartmentId,
            UUID ingestionSnapshotId,
            UUID currentSnapshotId,
            Instant evaluatedAt) {
        if (currentSnapshotId == null) {
            return new SourceAclEvaluation(AccessGate.UNKNOWN, ingestionSnapshotId, null);
        }
        AccessGate ingestionGate = evaluateSnapshot(
                actor, persistedDepartmentId, ingestionSnapshotId, evaluatedAt, false);
        AccessGate currentGate = evaluateSnapshot(
                actor, persistedDepartmentId, currentSnapshotId, evaluatedAt, true);
        return new SourceAclEvaluation(
                intersect(ingestionGate, currentGate), ingestionSnapshotId, currentSnapshotId);
    }

    private AccessGate evaluateSnapshot(
            CurrentActor actor,
            UUID persistedDepartmentId,
            UUID snapshotId,
            Instant evaluatedAt,
            boolean requireFresh) {
        SourceAclSnapshot snapshot = snapshots.findByIdAndOrganizationId(snapshotId, actor.organizationId())
                .orElse(null);
        if (snapshot == null
                || !seals.existsBySourceAclSnapshotIdAndOrganizationId(snapshotId, actor.organizationId())
                || !snapshot.isComplete()
                || (requireFresh && !snapshot.isUsableAt(evaluatedAt))) {
            return AccessGate.UNKNOWN;
        }

        List<SourceAclEntry> matching = entries.findBySourceAclSnapshotId(snapshotId).stream()
                .filter(entry -> matches(actor, persistedDepartmentId, entry))
                .toList();
        if (matching.stream().anyMatch(entry -> entry.getGate() == AccessGate.DENY)) {
            return AccessGate.DENY;
        }
        if (matching.stream().anyMatch(entry -> entry.getGate() == AccessGate.ALLOW)) {
            return AccessGate.ALLOW;
        }
        return snapshot.getDefaultGate();
    }

    private static AccessGate intersect(AccessGate ingestionGate, AccessGate currentGate) {
        if (ingestionGate == AccessGate.DENY || currentGate == AccessGate.DENY) {
            return AccessGate.DENY;
        }
        if (ingestionGate == AccessGate.ALLOW && currentGate == AccessGate.ALLOW) {
            return AccessGate.ALLOW;
        }
        return AccessGate.UNKNOWN;
    }

    private static boolean matches(CurrentActor actor, UUID persistedDepartmentId, SourceAclEntry entry) {
        return switch (entry.getPrincipalType()) {
            case ORGMEMORY_USER -> actor.userId().toString().equals(entry.getPrincipalKey());
            case ORGMEMORY_DEPARTMENT -> persistedDepartmentId != null
                    && persistedDepartmentId.toString().equals(entry.getPrincipalKey());
            case ORGMEMORY_ORGANIZATION -> actor.organizationId().toString().equals(entry.getPrincipalKey());
            // External principals are resolved through the verified mapping ledger in a later
            // phase; until then they grant nothing so the recheck stays fail-closed.
            case SOURCE_USER, SOURCE_GROUP -> false;
        };
    }
}
