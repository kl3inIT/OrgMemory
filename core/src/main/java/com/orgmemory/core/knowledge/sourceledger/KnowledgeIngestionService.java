package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.acl.RotateSourceAclCommand;
import com.orgmemory.core.knowledge.acl.SourceAclFacade;
import com.orgmemory.core.knowledge.acl.SourceAclRotationRef;
import com.orgmemory.core.knowledge.acl.SourceAclSnapshotRef;
import com.orgmemory.core.knowledge.acl.SourceAclTarget;

import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;

import com.orgmemory.core.organization.DepartmentRepository;
import com.orgmemory.core.organization.OrganizationRepository;
import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import com.orgmemory.core.shared.error.BusinessConflictException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import jakarta.persistence.EntityManager;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeIngestionService {

    private final OrganizationRepository organizations;
    private final DepartmentRepository departments;
    private final RawSourceObjectRepository rawSources;
    private final SourceAclFacade sourceAcls;
    private final NormalizedRecordRepository normalizedRecords;
    private final KnowledgeAssetPromotionPort assetPromotions;
    private final SourceObjectRepository sourceObjects;
    private final SourceRevisionRepository sourceRevisions;
    private final KnowledgePermissionPolicy permissionPolicy;
    private final SourceKnowledgeSpacePort knowledgeSpaces;
    private final EntityManager entityManager;

    public KnowledgeIngestionService(
            OrganizationRepository organizations,
            DepartmentRepository departments,
            RawSourceObjectRepository rawSources,
            SourceAclFacade sourceAcls,
            NormalizedRecordRepository normalizedRecords,
            KnowledgeAssetPromotionPort assetPromotions,
            SourceObjectRepository sourceObjects,
            SourceRevisionRepository sourceRevisions,
            KnowledgePermissionPolicy permissionPolicy,
            SourceKnowledgeSpacePort knowledgeSpaces,
            EntityManager entityManager) {
        this.organizations = organizations;
        this.departments = departments;
        this.rawSources = rawSources;
        this.sourceAcls = sourceAcls;
        this.normalizedRecords = normalizedRecords;
        this.assetPromotions = assetPromotions;
        this.sourceObjects = sourceObjects;
        this.sourceRevisions = sourceRevisions;
        this.permissionPolicy = permissionPolicy;
        this.knowledgeSpaces = knowledgeSpaces;
        this.entityManager = entityManager;
    }

    @Transactional
    public RawSourceRef registerRawSource(RegisterRawSourceCommand command) {
        return doRegister(command, false);
    }

    /**
     * Registers a source whose ACL carries external principals ({@code SOURCE_USER} /
     * {@code SOURCE_GROUP}). Membership is governed independently by its own snapshot/head.
     * The public {@link #registerRawSource} path rejects external principals because an
     * upload has no verified identity behind them; the connector is the trusted path that
     * carries that evidence, having observed and matched the principals first.
     */
    @Transactional
    public RawSourceRef registerConnectorSource(RegisterRawSourceCommand command) {
        return doRegister(command, true);
    }

    private RawSourceRef doRegister(
            RegisterRawSourceCommand command,
            boolean allowExternalPrincipals) {
        validateRawCommand(command, allowExternalPrincipals);
        String sourceSystem = command.sourceSystem().trim();
        String connectionKey = command.sourceConnectionKey().trim();
        String externalObjectId = command.externalObjectId().trim();
        String sourceVersion = command.sourceVersion().trim();
        String payloadSha = sha256(command.rawContent());
        String canonicalSourceUri = SourceCitationUri.canonicalize(command.sourceUri());
        Instant sourceModifiedAt = dbInstant(command.sourceModifiedAt());
        Instant aclValidUntil = dbInstant(command.aclValidUntil());
        acquireTransactionLock(sourceIdentityLockKey(
                command.organizationId(), sourceSystem, connectionKey, externalObjectId));

        var existing = rawSources
                .findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectIdAndSourceVersion(
                        command.organizationId(), sourceSystem, connectionKey, externalObjectId, sourceVersion);
        if (existing.isPresent()) {
            RawSourceObject raw = existing.get();
            SourceAclSnapshotRef snapshot = sourceAcls
                    .findInitialSnapshot(command.organizationId(), raw.getId())
                    .orElseThrow();
            if (!sameRevision(
                    raw,
                    snapshot,
                    command,
                    payloadSha,
                    canonicalSourceUri,
                    sourceModifiedAt,
                    aclValidUntil,
                    allowExternalPrincipals)) {
                throw new KnowledgeIngestionConflictException(
                        "The source revision already exists with different content or security metadata");
            }
            return rawRef(raw, snapshot);
        }

        Instant capturedAt = dbInstant(Instant.now());
        RawSourceObject raw = rawSources.save(new RawSourceObject(
                command, payloadSha, canonicalSourceUri, sourceModifiedAt));
        SourceAclSnapshotRef snapshot;
        try {
            snapshot = sourceAcls.createAndAdvance(
                    sourceAclTarget(raw),
                    command.expectedCurrentAclSnapshotId(),
                    command.aclCaptureStatus(),
                    command.defaultGate(),
                    aclValidUntil,
                    command.aclEntries(),
                    allowExternalPrincipals,
                    capturedAt);
        } catch (BusinessConflictException conflict) {
            throw new KnowledgeIngestionConflictException(conflict.getMessage());
        }
        return rawRef(raw, snapshot);
    }

    @Transactional
    public SourceAclRotationRef rotateSourceAcl(RotateSourceAclCommand command) {
        return doRotate(command, false);
    }

    /**
     * Rotates a source ACL to a new generation whose entries may carry external principals
     * without changing content. Source-group membership is reconciled independently before
     * this path is called.
     */
    @Transactional
    public SourceAclRotationRef rotateConnectorAcl(RotateSourceAclCommand command) {
        return doRotate(command, true);
    }

    private SourceAclRotationRef doRotate(
            RotateSourceAclCommand command,
            boolean allowExternalPrincipals) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.organizationId(), "organizationId");
        Objects.requireNonNull(command.rawSourceObjectId(), "rawSourceObjectId");
        Objects.requireNonNull(command.expectedCurrentSnapshotId(), "expectedCurrentSnapshotId");
        sourceAcls.validate(
                command.aclCaptureStatus(),
                command.defaultGate(),
                command.aclValidUntil(),
                command.aclEntries(),
                allowExternalPrincipals);

        RawSourceObject raw = rawSources
                .findByIdAndOrganizationId(command.rawSourceObjectId(), command.organizationId())
                .orElseThrow(KnowledgeResourceNotFoundException::new);
        acquireTransactionLock(sourceIdentityLockKey(
                raw.getOrganizationId(),
                raw.getSourceSystem(),
                raw.getSourceConnectionKey(),
                raw.getExternalObjectId()));
        try {
            return sourceAcls.rotate(
                    sourceAclTarget(raw),
                    command,
                    allowExternalPrincipals,
                    dbInstant(Instant.now()));
        } catch (BusinessConflictException conflict) {
            throw new KnowledgeIngestionConflictException(conflict.getMessage());
        }
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
                .orElseThrow(KnowledgeResourceNotFoundException::new);
        SourceAclSnapshotRef snapshot = sourceAcls.requireInitialSealedSnapshot(
                command.organizationId(), raw.getId());
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
                raw, snapshot.id(), command, contentSha, status, issue));
        raw.markNormalized();
        rawSources.save(raw);
        return normalizedRef(normalized);
    }

    @Transactional
    public SourceKnowledgeAssetRef promote(PromoteNormalizedRecordCommand command) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.organizationId(), "organizationId");
        Objects.requireNonNull(command.normalizedRecordId(), "normalizedRecordId");
        Objects.requireNonNull(command.knowledgeSpaceId(), "knowledgeSpaceId");
        Objects.requireNonNull(command.sourceObjectId(), "sourceObjectId");
        Objects.requireNonNull(command.sourceRevisionId(), "sourceRevisionId");
        if (command.orgMemoryGate() != AccessGate.ALLOW) {
            throw invalidIngestion(
                    "knowledge-ingestion.promotion-gate-invalid",
                    "Promotion requires an explicit OrgMemory ALLOW decision");
        }
        acquireTransactionLock("promote|" + command.normalizedRecordId());

        NormalizedRecord normalized = normalizedRecords
                .findByIdAndOrganizationId(command.normalizedRecordId(), command.organizationId())
                .orElseThrow(KnowledgeResourceNotFoundException::new);
        knowledgeSpaces.requireInOrganization(command.organizationId(), command.knowledgeSpaceId());
        var existing = assetPromotions.findByNormalizedRecord(
                command.organizationId(),
                command.knowledgeSpaceId(),
                normalized.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (normalized.getStatus() != NormalizedRecordStatus.READY) {
            throw new IllegalStateException("Only a ready normalized record can be promoted");
        }

        RawSourceObject raw = rawSources
                .findByIdAndOrganizationId(normalized.getRawSourceObjectId(), command.organizationId())
                .orElseThrow();
        Instant evaluatedAt = Instant.now();
        if (raw.getStatus() != RawSourceStatus.NORMALIZED
                || !sourceAcls.isReadyForPromotion(
                        command.organizationId(),
                        raw.getId(),
                        normalized.getSourceAclSnapshotId(),
                        evaluatedAt)) {
            throw new IllegalStateException("Source security metadata is not ready for promotion");
        }
        validatePromotableMetadata(normalized);

        SourceObject source = sourceObjects
                .findById(command.sourceObjectId())
                .filter(candidate -> candidate.getOrganizationId().equals(command.organizationId()))
                .orElseThrow(KnowledgeResourceNotFoundException::new);
        SourceRevision revision = sourceRevisions
                .findByIdAndOrganizationId(command.sourceRevisionId(), command.organizationId())
                .filter(candidate -> candidate.getSourceObjectId().equals(source.getId()))
                .orElseThrow(KnowledgeResourceNotFoundException::new);
        if (!source.getKnowledgeSpaceId().equals(command.knowledgeSpaceId())
                || !revision.getKnowledgeSpaceId().equals(command.knowledgeSpaceId())) {
            throw new KnowledgeIngestionConflictException(
                    "Source identity and revision must belong to the promotion Knowledge Space");
        }

        SourceKnowledgeAssetRef promoted = assetPromotions.promote(
                new KnowledgeAssetPromotionRequest(
                        command.organizationId(),
                        command.knowledgeSpaceId(),
                        source.getId(),
                        revision.getId(),
                        normalized.getId(),
                        normalized.getRawSourceObjectId(),
                        normalized.getSourceAclSnapshotId(),
                        normalized.getDepartmentId(),
                        normalized.getTitle(),
                        normalized.getNormalizedContent(),
                        normalized.getLanguage(),
                        normalized.getClassification(),
                        normalized.getDeclaredAccess(),
                        normalized.getContentSha256(),
                        command.orgMemoryGate()));
        normalized.markPromoted();
        normalizedRecords.save(normalized);
        return promoted;
    }

    private void validateRawCommand(RegisterRawSourceCommand command, boolean allowExternalPrincipals) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(command.organizationId(), "organizationId");
        if (!organizations.existsById(command.organizationId())) {
            throw invalidIngestion(
                    "knowledge-ingestion.organization-invalid",
                    "Organization does not exist");
        }
        if (command.departmentId() != null
                && !departments.existsByIdAndOrganizationId(command.departmentId(), command.organizationId())) {
            throw invalidIngestion(
                    "knowledge-ingestion.department-invalid",
                    "Department does not belong to the organization");
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

        try {
            SourceCitationUri.canonicalize(command.sourceUri());
        } catch (IllegalArgumentException invalidUri) {
            throw new BusinessValidationException(
                    "knowledge-ingestion.source-uri-invalid",
                    "The source URI is invalid",
                    invalidUri);
        }
        sourceAcls.validate(
                command.aclCaptureStatus(),
                command.defaultGate(),
                command.aclValidUntil(),
                command.aclEntries(),
                allowExternalPrincipals);
    }

    /** The current ACL head for a source object identity, if a generation has been sealed. */
    public Optional<SourceHeadView> findSourceHead(
            UUID organizationId, String sourceSystem, String sourceConnectionKey, String externalObjectId) {
        return sourceAcls
                .findHead(
                        organizationId,
                        sourceSystem.trim(),
                        sourceConnectionKey.trim(),
                        externalObjectId.trim())
                .map(head -> new SourceHeadView(
                        head.currentRawSourceObjectId(),
                        head.currentSnapshotId(),
                        head.aclGeneration(),
                        rawSources.findByIdAndOrganizationId(head.currentRawSourceObjectId(), organizationId)
                                .map(RawSourceObject::getSourceVersion)
                                .orElse(null)));
    }

    private NormalizationIssue normalizationIssue(
            RawSourceObject raw,
            SourceAclSnapshotRef snapshot,
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
            SourceAclSnapshotRef snapshot,
            RegisterRawSourceCommand command,
            String payloadSha,
            String canonicalSourceUri,
            Instant sourceModifiedAt,
            Instant aclValidUntil,
            boolean ignoreAclValidUntil) {
        return Objects.equals(raw.getDepartmentId(), command.departmentId())
                && raw.getObjectType().equals(command.objectType().trim())
                && raw.getTitle().equals(command.title().trim())
                && raw.getPayloadSha256().equals(payloadSha)
                && Objects.equals(raw.getSourceUri(), canonicalSourceUri)
                && Objects.equals(raw.getSourceModifiedAt(), sourceModifiedAt)
                && raw.getClassification() == command.classification()
                && raw.getDeclaredAccess() == command.declaredAccess()
                && sourceAcls.matchesSnapshot(
                        snapshot,
                        command.aclCaptureStatus(),
                        command.defaultGate(),
                        aclValidUntil,
                        command.aclEntries(),
                        ignoreAclValidUntil);
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

    private static String sha256(String value) {
        return com.orgmemory.core.shared.Digests.sha256(value);
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

    private static RawSourceRef rawRef(RawSourceObject raw, SourceAclSnapshotRef snapshot) {
        return new RawSourceRef(raw.getId(), snapshot.id(), raw.getStatus());
    }

    private static SourceAclTarget sourceAclTarget(RawSourceObject raw) {
        return new SourceAclTarget(
                raw.getId(),
                raw.getOrganizationId(),
                raw.getSourceSystem(),
                raw.getSourceConnectionKey(),
                raw.getExternalObjectId());
    }

    private static NormalizedRecordRef normalizedRef(NormalizedRecord normalized) {
        return new NormalizedRecordRef(
                normalized.getId(),
                normalized.getRawSourceObjectId(),
                normalized.getSourceAclSnapshotId(),
                normalized.getStatus(),
                normalized.getIssue());
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw invalidIngestion(
                    "knowledge-ingestion.field-required",
                    field + " must not be blank");
        }
    }

    private static BusinessValidationException invalidIngestion(
            String code,
            String message) {
        return new BusinessValidationException(code, message);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Instant dbInstant(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }

}
