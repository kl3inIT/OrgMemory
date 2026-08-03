package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageArtifact;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageAssetCommand;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackagePayloadPolicy;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageUpload;
import com.orgmemory.core.assetregistry.skillstorage.SkillPackageStoragePort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class SkillPackageAssetService implements SkillPackageAssetCommand {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(SkillPackageAssetService.class);

    private final SkillPackageStoragePort storage;
    private final SkillPackagePayloadPolicy payloadPolicy;
    private final AssetRegistryService assets;
    private final SkillPackageSupersessionCleanupService cleanup;

    SkillPackageAssetService(
            SkillPackageStoragePort storage,
            SkillPackagePayloadPolicy payloadPolicy,
            AssetRegistryService assets,
            SkillPackageSupersessionCleanupService cleanup) {
        this.storage = storage;
        this.payloadPolicy = payloadPolicy;
        this.assets = assets;
        this.cleanup = cleanup;
    }

    @Override
    public void requireCreate(CurrentActor actor, UUID knowledgeSpaceId) {
        assets.requireSkillCreate(actor, knowledgeSpaceId);
    }

    @Override
    public KnowledgeClassification requireEdit(
            CurrentActor actor, UUID assetId) {
        assets.requireSkillEdit(actor, assetId);
        AssetView current = assets.get(actor, assetId);
        if (current.type() != AssetType.SKILL || current.draft() == null) {
            throw new AssetNotFoundException();
        }
        return KnowledgeClassification.valueOf(
                current.draft().classification());
    }

    @Override
    public UUID importPackage(
            CurrentActor actor,
            String namespace,
            UUID knowledgeSpaceId,
            KnowledgeClassification classification,
            SkillPackageUpload upload) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(upload, "upload");
        requireCreate(actor, knowledgeSpaceId);
        SkillPackageStoragePort.StoredSkillPackage stored = null;
        UUID assetId = null;
        try {
            stored = store(actor, upload);
            SkillPackageArtifact artifact = artifact(stored);
            requireMatchingArtifact(upload.artifact(), artifact);
            payloadPolicy.validate(upload.payload(), artifact);
            assetId = assets.createValidatedSkillIdentity(
                    actor,
                    namespace,
                    upload.slug(),
                    knowledgeSpaceId,
                    draft(upload, classification),
                    stored);
            assets.projectCreated(actor, assetId);
            return assetId;
        } catch (RuntimeException failure) {
            if (assetId == null) {
                deleteIfStored(stored, failure);
            }
            throw failure;
        }
    }

    @Override
    public UUID replacePackage(
            CurrentActor actor,
            UUID assetId,
            long expectedLockVersion,
            SkillPackageUpload upload) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(assetId, "assetId");
        Objects.requireNonNull(upload, "upload");
        KnowledgeClassification classification = requireEdit(actor, assetId);
        SkillPackageStoragePort.StoredSkillPackage stored = null;
        SkillDraftReplacement replacement = null;
        try {
            stored = store(actor, upload);
            SkillPackageArtifact artifact = artifact(stored);
            requireMatchingArtifact(upload.artifact(), artifact);
            payloadPolicy.validate(upload.payload(), artifact);
            replacement = assets.replaceValidatedSkillDraft(
                    actor,
                    assetId,
                    expectedLockVersion,
                    draft(upload, classification),
                    stored);
        } catch (RuntimeException failure) {
            if (replacement == null) {
                deleteIfStored(stored, failure);
            }
            throw failure;
        }
        cleanupAfterCommit(replacement.supersessionId());
        return assetId;
    }

    private SkillPackageStoragePort.StoredSkillPackage store(
            CurrentActor actor, SkillPackageUpload upload) {
        return storage.put(
                new SkillPackageStoragePort.SkillPackageWriteRequest(
                        actor.organizationId(),
                        upload.packageId(),
                        upload.artifact().contentLength(),
                        upload.artifact().sha256(),
                        upload.storageMetadata()),
                upload.content());
    }

    private static AssetDraftInput draft(
            SkillPackageUpload upload,
            KnowledgeClassification classification) {
        return new AssetDraftInput(
                upload.title(),
                upload.summary(),
                classification.name(),
                upload.schemaVersion(),
                upload.payload());
    }

    private static SkillPackageArtifact artifact(
            SkillPackageStoragePort.StoredSkillPackage stored) {
        return new SkillPackageArtifact(
                stored.sha256(), stored.contentLength(), stored.mediaType());
    }

    private static void requireMatchingArtifact(
            SkillPackageArtifact expected, SkillPackageArtifact stored) {
        if (!expected.equals(stored)) {
            throw new IllegalArgumentException(
                    "Stored Skill package metadata does not match inspected bytes");
        }
    }

    private void cleanupAfterCommit(UUID supersessionId) {
        try {
            cleanup.cleanup(supersessionId);
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "Skill package supersession cleanup remains pending for {} ({})",
                    supersessionId,
                    failure.getClass().getSimpleName());
        }
    }

    private void deleteIfStored(
            SkillPackageStoragePort.StoredSkillPackage stored,
            Throwable failure) {
        if (stored == null) {
            return;
        }
        try {
            storage.delete(stored.objectKey());
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }
}
