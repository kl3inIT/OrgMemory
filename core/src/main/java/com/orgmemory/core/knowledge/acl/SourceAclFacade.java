package com.orgmemory.core.knowledge.acl;

import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.shared.Digests;
import com.orgmemory.core.shared.error.BusinessConflictException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ACL-owned API for source ingestion validation, persistence, and readiness queries. */
@Service
@Transactional(readOnly = true)
public class SourceAclFacade {

    private static final Duration MAX_ACL_TTL = Duration.ofHours(24);

    private final SourceAclSnapshotRepository snapshots;
    private final SourceAclEntryRepository entries;
    private final SourceAclSnapshotSealRepository seals;
    private final SourceAclHeadRepository heads;

    SourceAclFacade(
            SourceAclSnapshotRepository snapshots,
            SourceAclEntryRepository entries,
            SourceAclSnapshotSealRepository seals,
            SourceAclHeadRepository heads) {
        this.snapshots = snapshots;
        this.entries = entries;
        this.seals = seals;
        this.heads = heads;
    }

    public void validate(
            AclCaptureStatus captureStatus,
            AccessGate defaultGate,
            Instant validUntil,
            List<SourceAclEntryCommand> aclEntries,
            boolean allowExternalPrincipals) {
        Objects.requireNonNull(captureStatus, "aclCaptureStatus");
        Objects.requireNonNull(defaultGate, "defaultGate");
        List<SourceAclEntryCommand> safeEntries = List.copyOf(aclEntries);
        if (captureStatus == AclCaptureStatus.COMPLETE) {
            Instant canonicalValidUntil = dbInstant(validUntil);
            Instant now = dbInstant(Instant.now());
            if (canonicalValidUntil == null
                    || !canonicalValidUntil.isAfter(now)
                    || canonicalValidUntil.isAfter(now.plus(MAX_ACL_TTL))) {
                throw invalidAcl(
                        "knowledge-ingestion.acl-window-invalid",
                        "A complete ACL snapshot requires validUntil within the next 24 hours");
            }
        } else if (defaultGate != AccessGate.UNKNOWN
                || validUntil != null
                || !safeEntries.isEmpty()) {
            throw invalidAcl(
                    "knowledge-ingestion.acl-invalid",
                    "Unknown or unsupported ACL capture must remain UNKNOWN without entries or expiry");
        }

        Set<String> principals = new HashSet<>();
        for (SourceAclEntryCommand entry : safeEntries) {
            if (entry == null || entry.principalType() == null) {
                throw invalidAcl(
                        "knowledge-ingestion.acl-invalid",
                        "ACL principal type is required");
            }
            boolean external = entry.principalType() == SourcePrincipalType.SOURCE_USER
                    || entry.principalType() == SourcePrincipalType.SOURCE_GROUP;
            if (external && !allowExternalPrincipals) {
                throw invalidAcl(
                        "knowledge-ingestion.acl-invalid",
                        "External source ACLs require an identity mapping and cannot be marked complete");
            }
            if (entry.principalKey() == null || entry.principalKey().isBlank()) {
                throw invalidAcl(
                        "knowledge-ingestion.field-required",
                        "acl principal key must not be blank");
            }
            if (entry.gate() != AccessGate.ALLOW && entry.gate() != AccessGate.DENY) {
                throw invalidAcl(
                        "knowledge-ingestion.acl-invalid",
                        "ACL entries must be ALLOW or DENY");
            }
            String key = entry.principalType() + ":" + entry.principalKey().trim();
            if (!principals.add(key)) {
                throw invalidAcl(
                        "knowledge-ingestion.acl-invalid",
                        "ACL principal is duplicated: " + key);
            }
        }
    }

    public Optional<SourceAclSnapshotRef> findInitialSnapshot(
            UUID organizationId,
            UUID rawSourceObjectId) {
        return snapshots
                .findFirstByRawSourceObjectIdOrderByAclGenerationAsc(rawSourceObjectId)
                .filter(snapshot -> organizationId.equals(snapshot.getOrganizationId()))
                .map(SourceAclFacade::snapshotRef);
    }

    public Optional<SourceAclHeadRef> findHead(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId) {
        return heads
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        organizationId,
                        sourceSystem,
                        sourceConnectionKey,
                        externalObjectId)
                .map(SourceAclFacade::headRef);
    }

    @Transactional
    public SourceAclSnapshotRef createAndAdvance(
            SourceAclTarget target,
            UUID expectedCurrentSnapshotId,
            AclCaptureStatus captureStatus,
            AccessGate defaultGate,
            Instant validUntil,
            List<SourceAclEntryCommand> aclEntries,
            boolean allowExternalPrincipals,
            Instant capturedAt) {
        validate(
                captureStatus,
                defaultGate,
                validUntil,
                aclEntries,
                allowExternalPrincipals);
        SourceAclHead head = heads
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        target.organizationId(),
                        target.sourceSystem(),
                        target.sourceConnectionKey(),
                        target.externalObjectId())
                .orElse(null);
        requireExpectedHead(expectedCurrentSnapshotId, head);
        long generation = head == null ? 1 : head.getAclGeneration() + 1;
        Instant canonicalCapturedAt = dbInstant(capturedAt);
        Instant canonicalValidUntil = dbInstant(validUntil);
        validateSnapshotWindow(captureStatus, canonicalCapturedAt, canonicalValidUntil);
        SourceAclSnapshot snapshot = snapshots.saveAndFlush(new SourceAclSnapshot(
                target.organizationId(),
                target.rawSourceObjectId(),
                generation,
                captureStatus,
                defaultGate,
                captureStatus == AclCaptureStatus.COMPLETE
                        ? aclSha(captureStatus, defaultGate, aclEntries)
                        : null,
                canonicalCapturedAt,
                canonicalValidUntil));
        persistEntriesAndSeal(target.organizationId(), snapshot, aclEntries, canonicalCapturedAt);
        if (head == null) {
            heads.save(new SourceAclHead(target, snapshot));
        } else {
            head.advance(target, snapshot);
            heads.save(head);
        }
        return snapshotRef(snapshot);
    }

    @Transactional
    public SourceAclRotationRef rotate(
            SourceAclTarget target,
            RotateSourceAclCommand command,
            boolean allowExternalPrincipals,
            Instant capturedAt) {
        validate(
                command.aclCaptureStatus(),
                command.defaultGate(),
                command.aclValidUntil(),
                command.aclEntries(),
                allowExternalPrincipals);
        SourceAclHead head = heads
                .findForRawSourceObject(target.rawSourceObjectId(), target.organizationId())
                .orElseThrow(() -> new IllegalStateException("Source ACL head is missing"));
        if (!head.getCurrentRawSourceObjectId().equals(target.rawSourceObjectId())) {
            throw conflict("ACL rotation must target the current raw source revision");
        }

        Instant validUntil = dbInstant(command.aclValidUntil());
        String aclSha = command.aclCaptureStatus() == AclCaptureStatus.COMPLETE
                ? aclSha(command.aclCaptureStatus(), command.defaultGate(), command.aclEntries())
                : null;
        SourceAclSnapshot current = snapshots
                .findByIdAndOrganizationId(head.getCurrentSnapshotId(), target.organizationId())
                .orElseThrow();
        if ((allowExternalPrincipals && sameAcl(current, command, validUntil, aclSha, true))
                || sameAcl(current, command, validUntil, aclSha, false)) {
            return rotationRef(current);
        }
        if (!command.expectedCurrentSnapshotId().equals(current.getId())) {
            throw conflict("Source ACL head changed before this rotation was applied");
        }

        Instant canonicalCapturedAt = dbInstant(capturedAt);
        validateSnapshotWindow(command.aclCaptureStatus(), canonicalCapturedAt, validUntil);
        SourceAclSnapshot snapshot = snapshots.saveAndFlush(new SourceAclSnapshot(
                target.organizationId(),
                target.rawSourceObjectId(),
                head.getAclGeneration() + 1,
                command.aclCaptureStatus(),
                command.defaultGate(),
                aclSha,
                canonicalCapturedAt,
                validUntil));
        persistEntriesAndSeal(
                target.organizationId(), snapshot, command.aclEntries(), canonicalCapturedAt);
        head.advance(target, snapshot);
        heads.save(head);
        return rotationRef(snapshot);
    }

    public SourceAclSnapshotRef requireInitialSealedSnapshot(
            UUID organizationId,
            UUID rawSourceObjectId) {
        SourceAclSnapshot snapshot = snapshots
                .findFirstByRawSourceObjectIdOrderByAclGenerationAsc(rawSourceObjectId)
                .filter(candidate -> organizationId.equals(candidate.getOrganizationId()))
                .orElseThrow();
        if (!seals.existsBySourceAclSnapshotIdAndOrganizationId(snapshot.getId(), organizationId)) {
            throw new IllegalStateException("Source ACL snapshot is not sealed");
        }
        return snapshotRef(snapshot);
    }

    public boolean isReadyForPromotion(
            UUID organizationId,
            UUID rawSourceObjectId,
            UUID sourceAclSnapshotId,
            Instant evaluatedAt) {
        SourceAclSnapshot snapshot = snapshots
                .findByIdAndOrganizationId(sourceAclSnapshotId, organizationId)
                .orElseThrow();
        SourceAclHead head = heads.findForRawSourceObject(rawSourceObjectId, organizationId)
                .orElseThrow();
        SourceAclSnapshot current = snapshots
                .findByIdAndOrganizationId(head.getCurrentSnapshotId(), organizationId)
                .orElseThrow();
        return snapshot.isUsableAt(evaluatedAt)
                && current.isUsableAt(evaluatedAt)
                && seals.existsBySourceAclSnapshotIdAndOrganizationId(snapshot.getId(), organizationId)
                && seals.existsBySourceAclSnapshotIdAndOrganizationId(current.getId(), organizationId);
    }

    public boolean matchesSnapshot(
            SourceAclSnapshotRef snapshot,
            AclCaptureStatus captureStatus,
            AccessGate defaultGate,
            Instant validUntil,
            List<SourceAclEntryCommand> aclEntries,
            boolean ignoreValidUntil) {
        String aclSha = captureStatus == AclCaptureStatus.COMPLETE
                ? aclSha(captureStatus, defaultGate, aclEntries)
                : null;
        return snapshot.captureStatus() == captureStatus
                && snapshot.defaultGate() == defaultGate
                && (ignoreValidUntil || Objects.equals(snapshot.validUntil(), validUntil))
                && Objects.equals(snapshot.aclSha256(), aclSha);
    }

    private void persistEntriesAndSeal(
            UUID organizationId,
            SourceAclSnapshot snapshot,
            List<SourceAclEntryCommand> aclEntries,
            Instant sealedAt) {
        entries.saveAllAndFlush(aclEntries.stream()
                .map(entry -> new SourceAclEntry(
                        organizationId, snapshot.getId(), entry, sealedAt))
                .toList());
        seals.saveAndFlush(new SourceAclSnapshotSeal(
                snapshot.getId(),
                organizationId,
                aclEntries.size(),
                Digests.sha256(canonicalEntries(aclEntries)),
                sealedAt));
    }

    private static boolean sameAcl(
            SourceAclSnapshot snapshot,
            RotateSourceAclCommand command,
            Instant validUntil,
            String aclSha,
            boolean ignoreValidUntil) {
        return snapshot.getCaptureStatus() == command.aclCaptureStatus()
                && snapshot.getDefaultGate() == command.defaultGate()
                && (ignoreValidUntil || Objects.equals(snapshot.getValidUntil(), validUntil))
                && Objects.equals(snapshot.getAclSha256(), aclSha);
    }

    private static void requireExpectedHead(UUID expectedSnapshotId, SourceAclHead head) {
        if (head == null) {
            if (expectedSnapshotId != null) {
                throw conflict("The source ACL head does not exist for the expected snapshot");
            }
            return;
        }
        if (expectedSnapshotId == null || !expectedSnapshotId.equals(head.getCurrentSnapshotId())) {
            throw conflict("The source ACL head changed before this source revision was registered");
        }
    }

    private static void validateSnapshotWindow(
            AclCaptureStatus captureStatus,
            Instant capturedAt,
            Instant validUntil) {
        if (captureStatus == AclCaptureStatus.COMPLETE
                && (validUntil == null
                        || !validUntil.isAfter(capturedAt)
                        || validUntil.isAfter(capturedAt.plus(MAX_ACL_TTL)))) {
            throw conflict(
                    "ACL validity expired or exceeded the 24-hour refresh window while waiting to persist");
        }
    }

    private static String aclSha(
            AclCaptureStatus captureStatus,
            AccessGate defaultGate,
            List<SourceAclEntryCommand> aclEntries) {
        return Digests.sha256(captureStatus + "|" + defaultGate + "|" + canonicalEntries(aclEntries));
    }

    private static String canonicalEntries(List<SourceAclEntryCommand> aclEntries) {
        return aclEntries.stream()
                .sorted(Comparator.comparing((SourceAclEntryCommand entry) -> entry.principalType().name())
                        .thenComparing(entry -> entry.principalKey().trim())
                        .thenComparing(entry -> entry.gate().name()))
                .map(entry -> entry.principalType() + ":" + entry.principalKey().trim() + ":" + entry.gate())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
    }

    private static SourceAclHeadRef headRef(SourceAclHead head) {
        return new SourceAclHeadRef(
                head.getCurrentRawSourceObjectId(),
                head.getCurrentSnapshotId(),
                head.getAclGeneration());
    }

    private static SourceAclSnapshotRef snapshotRef(SourceAclSnapshot snapshot) {
        return new SourceAclSnapshotRef(
                snapshot.getId(),
                snapshot.getRawSourceObjectId(),
                snapshot.getAclGeneration(),
                snapshot.getCaptureStatus(),
                snapshot.getDefaultGate(),
                snapshot.getAclSha256(),
                snapshot.getCapturedAt(),
                snapshot.getValidUntil());
    }

    private static SourceAclRotationRef rotationRef(SourceAclSnapshot snapshot) {
        return new SourceAclRotationRef(
                snapshot.getRawSourceObjectId(),
                snapshot.getId(),
                snapshot.getAclGeneration(),
                snapshot.getCaptureStatus());
    }

    private static BusinessValidationException invalidAcl(String code, String message) {
        return new BusinessValidationException(code, message);
    }

    private static BusinessConflictException conflict(String message) {
        return new BusinessConflictException("knowledge-ingestion.conflict", message);
    }

    private static Instant dbInstant(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }
}
