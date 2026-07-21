package com.orgmemory.core.knowledge;

import com.orgmemory.core.organization.DepartmentRepository;
import com.orgmemory.core.organization.OrganizationRepository;
import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import jakarta.persistence.EntityManager;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeIngestionService {

    private static final Duration MAX_ACL_TTL = Duration.ofHours(24);

    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;
    private final RawSourceObjectRepository rawSources;
    private final SourceAclSnapshotRepository snapshots;
    private final SourceAclEntryRepository aclEntries;
    private final SourceAclSnapshotSealRepository aclSeals;
    private final SourceAclHeadRepository aclHeads;
    private final NormalizedRecordRepository normalizedRecords;
    private final KnowledgeAssetRepository knowledgeAssets;
    private final KnowledgePermissionPolicy permissionPolicy;
    private final EntityManager entityManager;

    public KnowledgeIngestionService(
            OrganizationRepository organizations,
            DepartmentRepository departments,
            RawSourceObjectRepository rawSources,
            SourceAclSnapshotRepository snapshots,
            SourceAclEntryRepository aclEntries,
            SourceAclSnapshotSealRepository aclSeals,
            SourceAclHeadRepository aclHeads,
            NormalizedRecordRepository normalizedRecords,
            KnowledgeAssetRepository knowledgeAssets,
            KnowledgePermissionPolicy permissionPolicy,
            EntityManager entityManager) {
        this.organizations = organizations;
        this.departments = departments;
        this.rawSources = rawSources;
        this.snapshots = snapshots;
        this.aclEntries = aclEntries;
        this.aclSeals = aclSeals;
        this.aclHeads = aclHeads;
        this.normalizedRecords = normalizedRecords;
        this.knowledgeAssets = knowledgeAssets;
        this.permissionPolicy = permissionPolicy;
        this.entityManager = entityManager;
    }

    @Transactional
    public RawSourceRef registerRawSource(RegisterRawSourceCommand command) {
        validateRawCommand(command);
        String sourceSystem = command.sourceSystem().trim();
        String connectionKey = command.sourceConnectionKey().trim();
        String externalObjectId = command.externalObjectId().trim();
        String sourceVersion = command.sourceVersion().trim();
        String payloadSha = sha256(command.rawContent());
        String canonicalSourceUri = SourceCitationUri.canonicalize(command.sourceUri());
        Instant sourceModifiedAt = dbInstant(command.sourceModifiedAt());
        Instant aclValidUntil = dbInstant(command.aclValidUntil());
        String aclSha = command.aclCaptureStatus() == AclCaptureStatus.COMPLETE
                ? aclSha(
                        command.aclCaptureStatus(),
                        command.defaultGate(),
                        aclValidUntil,
                        command.aclEntries())
                : null;
        acquireTransactionLock(sourceIdentityLockKey(
                command.organizationId(), sourceSystem, connectionKey, externalObjectId));

        var existing = rawSources
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectIdAndSourceVersion(
                        command.organizationId(), sourceSystem, connectionKey, externalObjectId, sourceVersion);
        if (existing.isPresent()) {
            RawSourceObject raw = existing.get();
            SourceAclSnapshot snapshot = snapshots
                    .findFirstByRawSourceObjectIdOrderByAclGenerationAsc(raw.getId())
                    .orElseThrow();
            if (!sameRevision(
                    raw,
                    snapshot,
                    command,
                    payloadSha,
                    canonicalSourceUri,
                    sourceModifiedAt,
                    aclValidUntil,
                    aclSha)) {
                throw new KnowledgeIngestionConflictException(
                        "The source revision already exists with different content or security metadata");
            }
            return rawRef(raw, snapshot);
        }

        SourceAclHead head = aclHeads
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        command.organizationId(), sourceSystem, connectionKey, externalObjectId)
                .orElse(null);
        requireExpectedHead(command.expectedCurrentAclSnapshotId(), head);
        long aclGeneration = head == null ? 1 : head.getAclGeneration() + 1;
        Instant capturedAt = dbInstant(Instant.now());
        validateSnapshotWindow(command.aclCaptureStatus(), capturedAt, aclValidUntil);
        RawSourceObject raw = rawSources.save(new RawSourceObject(
                command, payloadSha, canonicalSourceUri, sourceModifiedAt));
        SourceAclSnapshot snapshot = snapshots.saveAndFlush(new SourceAclSnapshot(
                command.organizationId(),
                raw.getId(),
                aclGeneration,
                command.aclCaptureStatus(),
                command.defaultGate(),
                aclSha,
                capturedAt,
                aclValidUntil));
        List<SourceAclEntry> entries = command.aclEntries().stream()
                .map(entry -> new SourceAclEntry(command.organizationId(), snapshot.getId(), entry, capturedAt))
                .toList();
        aclEntries.saveAllAndFlush(entries);
        sealSnapshot(snapshot, command.aclEntries(), capturedAt);
        if (head == null) {
            aclHeads.save(new SourceAclHead(raw, snapshot));
        } else {
            head.advance(raw, snapshot);
            aclHeads.save(head);
        }
        return rawRef(raw, snapshot);
    }

    @Transactional
    public SourceAclRotationRef rotateSourceAcl(RotateSourceAclCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.organizationId(), "organizationId");
        Objects.requireNonNull(command.rawSourceObjectId(), "rawSourceObjectId");
        Objects.requireNonNull(command.expectedCurrentSnapshotId(), "expectedCurrentSnapshotId");
        validateAcl(
                command.aclCaptureStatus(),
                command.defaultGate(),
                command.aclValidUntil(),
                command.aclEntries());

        RawSourceObject raw = rawSources
                .findByIdAndOrganizationId(command.rawSourceObjectId(), command.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Raw source object was not found"));
        acquireTransactionLock(sourceIdentityLockKey(
                raw.getOrganizationId(),
                raw.getSourceSystem(),
                raw.getSourceConnectionKey(),
                raw.getExternalObjectId()));
        SourceAclHead head = aclHeads.findForRawSourceObject(raw.getId(), command.organizationId())
                .orElseThrow(() -> new IllegalStateException("Source ACL head is missing"));
        if (!head.getCurrentRawSourceObjectId().equals(raw.getId())) {
            throw new KnowledgeIngestionConflictException(
                    "ACL rotation must target the current raw source revision");
        }

        Instant validUntil = dbInstant(command.aclValidUntil());
        String aclSha = command.aclCaptureStatus() == AclCaptureStatus.COMPLETE
                ? aclSha(
                        command.aclCaptureStatus(),
                        command.defaultGate(),
                        validUntil,
                        command.aclEntries())
                : null;
        SourceAclSnapshot current = snapshots
                .findByIdAndOrganizationId(head.getCurrentSnapshotId(), command.organizationId())
                .orElseThrow();
        if (sameAcl(
                current,
                command.aclCaptureStatus(),
                command.defaultGate(),
                validUntil,
                aclSha)) {
            return rotationRef(current);
        }
        if (!command.expectedCurrentSnapshotId().equals(current.getId())) {
            throw new KnowledgeIngestionConflictException(
                    "Source ACL head changed before this rotation was applied");
        }

        Instant capturedAt = dbInstant(Instant.now());
        validateSnapshotWindow(command.aclCaptureStatus(), capturedAt, validUntil);
        SourceAclSnapshot snapshot = snapshots.saveAndFlush(new SourceAclSnapshot(
                command.organizationId(),
                raw.getId(),
                head.getAclGeneration() + 1,
                command.aclCaptureStatus(),
                command.defaultGate(),
                aclSha,
                capturedAt,
                validUntil));
        aclEntries.saveAllAndFlush(command.aclEntries().stream()
                .map(entry -> new SourceAclEntry(
                        command.organizationId(), snapshot.getId(), entry, capturedAt))
                .toList());
        sealSnapshot(snapshot, command.aclEntries(), capturedAt);
        head.advance(raw, snapshot);
        aclHeads.save(head);
        return rotationRef(snapshot);
    }

    @Transactional
    public NormalizedRecordRef normalize(NormalizeRawSourceCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.organizationId(), "organizationId");
        Objects.requireNonNull(command.rawSourceObjectId(), "rawSourceObjectId");
        requireText(command.normalizerVersion(), "normalizerVersion");
        acquireTransactionLock("normalize|" + command.rawSourceObjectId() + "|" + command.normalizerVersion().trim());

        RawSourceObject raw = rawSources
                .findByIdAndOrganizationId(command.rawSourceObjectId(), command.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Raw source object was not found"));
        SourceAclSnapshot snapshot = snapshots
                .findFirstByRawSourceObjectIdOrderByAclGenerationAsc(raw.getId())
                .orElseThrow();
        if (!aclSeals.existsBySourceAclSnapshotIdAndOrganizationId(snapshot.getId(), command.organizationId())) {
            throw new IllegalStateException("Source ACL snapshot is not sealed");
        }
        String normalizedContent = blankToNull(command.normalizedContent());
        String contentSha = normalizedContent == null ? null : sha256(normalizedContent);

        var existing = normalizedRecords.findByRawSourceObjectIdAndNormalizerVersion(
                raw.getId(), command.normalizerVersion().trim());
        if (existing.isPresent()) {
            NormalizedRecord normalized = existing.get();
            if (!sameNormalization(normalized, command, contentSha)) {
                throw new KnowledgeIngestionConflictException(
                        "The normalizer version already exists with different output");
            }
            return normalizedRef(normalized);
        }

        NormalizationIssue issue = normalizationIssue(raw, snapshot, command, Instant.now());
        NormalizedRecordStatus status = issue == null
                ? NormalizedRecordStatus.READY
                : NormalizedRecordStatus.QUARANTINED;
        NormalizedRecord normalized = normalizedRecords.save(new NormalizedRecord(
                raw, snapshot, command, contentSha, status, issue));
        raw.markNormalized();
        rawSources.save(raw);
        return normalizedRef(normalized);
    }

    @Transactional
    public KnowledgeAssetRef promote(PromoteNormalizedRecordCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.organizationId(), "organizationId");
        Objects.requireNonNull(command.normalizedRecordId(), "normalizedRecordId");
        if (command.orgMemoryGate() != AccessGate.ALLOW) {
            throw new IllegalArgumentException("Promotion requires an explicit OrgMemory ALLOW decision");
        }
        acquireTransactionLock("promote|" + command.normalizedRecordId());

        NormalizedRecord normalized = normalizedRecords
                .findByIdAndOrganizationId(command.normalizedRecordId(), command.organizationId())
                .orElseThrow(() -> new IllegalArgumentException("Normalized record was not found"));
        var existing = knowledgeAssets.findByNormalizedRecordId(normalized.getId());
        if (existing.isPresent()) {
            return knowledgeRef(existing.get());
        }
        if (normalized.getStatus() != NormalizedRecordStatus.READY) {
            throw new IllegalStateException("Only a ready normalized record can be promoted");
        }

        RawSourceObject raw = rawSources
                .findByIdAndOrganizationId(normalized.getRawSourceObjectId(), command.organizationId())
                .orElseThrow();
        SourceAclSnapshot snapshot = snapshots
                .findByIdAndOrganizationId(normalized.getSourceAclSnapshotId(), command.organizationId())
                .orElseThrow();
        SourceAclHead head = aclHeads.findForRawSourceObject(raw.getId(), command.organizationId())
                .orElseThrow();
        SourceAclSnapshot currentSnapshot = snapshots
                .findByIdAndOrganizationId(head.getCurrentSnapshotId(), command.organizationId())
                .orElseThrow();
        Instant evaluatedAt = Instant.now();
        if (raw.getStatus() != RawSourceStatus.NORMALIZED
                || !snapshot.isUsableAt(evaluatedAt)
                || !currentSnapshot.isUsableAt(evaluatedAt)
                || !aclSeals.existsBySourceAclSnapshotIdAndOrganizationId(
                        snapshot.getId(), command.organizationId())
                || !aclSeals.existsBySourceAclSnapshotIdAndOrganizationId(
                        currentSnapshot.getId(), command.organizationId())) {
            throw new IllegalStateException("Source security metadata is not ready for promotion");
        }
        validatePromotableMetadata(normalized);

        KnowledgeAsset asset = knowledgeAssets.save(new KnowledgeAsset(
                normalized, command.orgMemoryGate(), Instant.now()));
        normalized.markPromoted();
        normalizedRecords.save(normalized);
        return knowledgeRef(asset);
    }

    @Transactional
    public KnowledgeAssetRef retire(UUID organizationId, UUID knowledgeAssetId) {
        KnowledgeAsset asset = knowledgeAssets.findByIdAndOrganizationId(knowledgeAssetId, organizationId)
                .orElseThrow(KnowledgeAssetNotFoundException::new);
        asset.retire(Instant.now());
        return knowledgeRef(knowledgeAssets.save(asset));
    }

    private void validateRawCommand(RegisterRawSourceCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.organizationId(), "organizationId");
        if (!organizations.existsById(command.organizationId())) {
            throw new IllegalArgumentException("Organization does not exist");
        }
        if (command.departmentId() != null
                && !departments.existsByIdAndOrganizationId(command.departmentId(), command.organizationId())) {
            throw new IllegalArgumentException("Department does not belong to the organization");
        }
        requireText(command.sourceSystem(), "sourceSystem");
        requireText(command.sourceConnectionKey(), "sourceConnectionKey");
        requireText(command.externalObjectId(), "externalObjectId");
        requireText(command.sourceVersion(), "sourceVersion");
        requireText(command.objectType(), "objectType");
        requireText(command.title(), "title");
        requireText(command.rawContent(), "rawContent");
        Objects.requireNonNull(command.aclCaptureStatus(), "aclCaptureStatus");
        Objects.requireNonNull(command.defaultGate(), "defaultGate");

        SourceCitationUri.canonicalize(command.sourceUri());
        validateAcl(
                command.aclCaptureStatus(),
                command.defaultGate(),
                command.aclValidUntil(),
                command.aclEntries());
    }

    private void validateAcl(
            AclCaptureStatus captureStatus,
            AccessGate defaultGate,
            Instant validUntil,
            List<SourceAclEntryCommand> entries) {
        Objects.requireNonNull(captureStatus, "aclCaptureStatus");
        Objects.requireNonNull(defaultGate, "defaultGate");
        if (captureStatus == AclCaptureStatus.COMPLETE) {
            Instant canonicalValidUntil = dbInstant(validUntil);
            Instant now = dbInstant(Instant.now());
            if (canonicalValidUntil == null
                    || !canonicalValidUntil.isAfter(now)
                    || canonicalValidUntil.isAfter(now.plus(MAX_ACL_TTL))) {
                throw new IllegalArgumentException(
                        "A complete ACL snapshot requires validUntil within the next 24 hours");
            }
        } else if (defaultGate != AccessGate.UNKNOWN
                || validUntil != null
                || !entries.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unknown or unsupported ACL capture must remain UNKNOWN without entries or expiry");
        }

        Set<String> principals = new HashSet<>();
        for (SourceAclEntryCommand entry : entries) {
            if (entry == null || entry.principalType() == null) {
                throw new IllegalArgumentException("ACL principal type is required");
            }
            if (entry.principalType() == SourcePrincipalType.SOURCE_GROUP) {
                throw new IllegalArgumentException(
                        "Source group ACLs require an identity mapping and cannot be marked complete");
            }
            requireText(entry.principalKey(), "acl principal key");
            if (entry.gate() != AccessGate.ALLOW && entry.gate() != AccessGate.DENY) {
                throw new IllegalArgumentException("ACL entries must be ALLOW or DENY");
            }
            String key = entry.principalType() + ":" + entry.principalKey().trim();
            if (!principals.add(key)) {
                throw new IllegalArgumentException("ACL principal is duplicated: " + key);
            }
        }
    }

    private NormalizationIssue normalizationIssue(
            RawSourceObject raw,
            SourceAclSnapshot snapshot,
            NormalizeRawSourceCommand command,
            Instant evaluatedAt) {
        if (blankToNull(command.title()) == null || blankToNull(command.normalizedContent()) == null) {
            return NormalizationIssue.CONTENT_EMPTY;
        }
        if (!snapshot.isUsableAt(evaluatedAt)) {
            return NormalizationIssue.ACL_NOT_COMPLETE;
        }
        if (raw.getClassification() == null) {
            return NormalizationIssue.CLASSIFICATION_MISSING;
        }
        if (raw.getDeclaredAccess() == null) {
            return NormalizationIssue.DECLARED_ACCESS_MISSING;
        }
        if (permissionPolicy.requiredScope(raw.getClassification()) != raw.getDeclaredAccess()) {
            return NormalizationIssue.DECLARED_ACCESS_MISMATCH;
        }
        if (raw.getClassification() == KnowledgeClassification.CONFIDENTIAL && raw.getDepartmentId() == null) {
            return NormalizationIssue.DEPARTMENT_MISSING;
        }
        return null;
    }

    private void validatePromotableMetadata(NormalizedRecord normalized) {
        KnowledgeClassification classification = normalized.getClassification();
        DeclaredAccessScope declaredAccess = normalized.getDeclaredAccess();
        if (classification == null
                || declaredAccess == null
                || permissionPolicy.requiredScope(classification) != declaredAccess) {
            throw new IllegalStateException("Normalized permission metadata is not promotable");
        }
        if (classification == KnowledgeClassification.CONFIDENTIAL && normalized.getDepartmentId() == null) {
            throw new IllegalStateException("Confidential knowledge requires a department");
        }
    }

    private boolean sameRevision(
            RawSourceObject raw,
            SourceAclSnapshot snapshot,
            RegisterRawSourceCommand command,
            String payloadSha,
            String canonicalSourceUri,
            Instant sourceModifiedAt,
            Instant aclValidUntil,
            String aclSha) {
        return Objects.equals(raw.getDepartmentId(), command.departmentId())
                && raw.getObjectType().equals(command.objectType().trim())
                && raw.getTitle().equals(command.title().trim())
                && raw.getPayloadSha256().equals(payloadSha)
                && Objects.equals(raw.getSourceUri(), canonicalSourceUri)
                && Objects.equals(raw.getSourceModifiedAt(), sourceModifiedAt)
                && raw.getClassification() == command.classification()
                && raw.getDeclaredAccess() == command.declaredAccess()
                && snapshot.getCaptureStatus() == command.aclCaptureStatus()
                && snapshot.getDefaultGate() == command.defaultGate()
                && Objects.equals(snapshot.getAclSha256(), aclSha)
                && Objects.equals(snapshot.getValidUntil(), aclValidUntil);
    }

    private static boolean sameAcl(
            SourceAclSnapshot snapshot,
            AclCaptureStatus captureStatus,
            AccessGate defaultGate,
            Instant validUntil,
            String aclSha) {
        return snapshot.getCaptureStatus() == captureStatus
                && snapshot.getDefaultGate() == defaultGate
                && Objects.equals(snapshot.getValidUntil(), validUntil)
                && Objects.equals(snapshot.getAclSha256(), aclSha);
    }

    private static void requireExpectedHead(UUID expectedSnapshotId, SourceAclHead head) {
        if (head == null) {
            if (expectedSnapshotId != null) {
                throw new KnowledgeIngestionConflictException(
                        "The source ACL head does not exist for the expected snapshot");
            }
            return;
        }
        if (expectedSnapshotId == null || !expectedSnapshotId.equals(head.getCurrentSnapshotId())) {
            throw new KnowledgeIngestionConflictException(
                    "The source ACL head changed before this source revision was registered");
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
            throw new IllegalArgumentException(
                    "ACL validity expired or exceeded the 24-hour refresh window while waiting to persist");
        }
    }

    private void sealSnapshot(
            SourceAclSnapshot snapshot,
            List<SourceAclEntryCommand> entries,
            Instant sealedAt) {
        aclSeals.saveAndFlush(new SourceAclSnapshotSeal(
                snapshot.getId(),
                snapshot.getOrganizationId(),
                entries.size(),
                sha256(canonicalEntries(entries)),
                sealedAt));
    }

    private static boolean sameNormalization(
            NormalizedRecord normalized,
            NormalizeRawSourceCommand command,
            String contentSha) {
        return normalized.getNormalizerVersion().equals(command.normalizerVersion().trim())
                && Objects.equals(normalized.getTitle(), blankToNull(command.title()))
                && Objects.equals(normalized.getNormalizedContent(), blankToNull(command.normalizedContent()))
                && Objects.equals(normalized.getLanguage(), blankToNull(command.language()))
                && Objects.equals(normalized.getContentSha256(), contentSha);
    }

    private static String aclSha(
            AclCaptureStatus captureStatus,
            AccessGate defaultGate,
            Instant validUntil,
            List<SourceAclEntryCommand> aclEntries) {
        return sha256(captureStatus
                + "|" + defaultGate
                + "|" + validUntil
                + "|" + canonicalEntries(aclEntries));
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

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void acquireTransactionLock(String lockKey) {
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(lockKey.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
        long advisoryLockId = ByteBuffer.wrap(digest).getLong();
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
                .setParameter(1, advisoryLockId)
                .getSingleResult();
    }

    private static String sourceIdentityLockKey(
            UUID organizationId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalObjectId) {
        return "source|" + organizationId
                + "|" + sourceSystem
                + "|" + sourceConnectionKey
                + "|" + externalObjectId;
    }

    private static RawSourceRef rawRef(RawSourceObject raw, SourceAclSnapshot snapshot) {
        return new RawSourceRef(raw.getId(), snapshot.getId(), raw.getStatus());
    }

    private static SourceAclRotationRef rotationRef(SourceAclSnapshot snapshot) {
        return new SourceAclRotationRef(
                snapshot.getRawSourceObjectId(),
                snapshot.getId(),
                snapshot.getAclGeneration(),
                snapshot.getCaptureStatus());
    }

    private static NormalizedRecordRef normalizedRef(NormalizedRecord normalized) {
        return new NormalizedRecordRef(
                normalized.getId(),
                normalized.getRawSourceObjectId(),
                normalized.getSourceAclSnapshotId(),
                normalized.getStatus(),
                normalized.getIssue());
    }

    private static KnowledgeAssetRef knowledgeRef(KnowledgeAsset asset) {
        return new KnowledgeAssetRef(
                asset.getId(),
                asset.getNormalizedRecordId(),
                asset.getRawSourceObjectId(),
                asset.getSourceAclSnapshotId(),
                asset.getStatus());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant dbInstant(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

}
