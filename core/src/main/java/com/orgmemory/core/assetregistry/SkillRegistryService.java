package com.orgmemory.core.assetregistry;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.error.BusinessUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SkillRegistryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillRegistryService.class);

    private final SkillPackageInspector inspector;
    private final SkillPackageStoragePort storage;
    private final AssetRegistryService assets;
    private final SkillPackageSupersessionCleanupService cleanup;
    private final ObjectMapper json = JsonMapper.builder().build();

    SkillRegistryService(
            SkillPackageInspector inspector,
            SkillPackageStoragePort storage,
            AssetRegistryService assets,
            SkillPackageSupersessionCleanupService cleanup) {
        this.inspector = inspector;
        this.storage = storage;
        this.assets = assets;
        this.cleanup = cleanup;
    }

    public SkillPackageInspection inspectPackage(
            CurrentActor actor, long contentLength, InputStream content) {
        Objects.requireNonNull(actor, "actor");
        try (SkillPackageInspector.StagedSkillPackage staged =
                inspector.inspect(content, contentLength)) {
            return new SkillPackageInspection(
                    staged.metadata().name(),
                    staged.metadata().description(),
                    staged.metadata().license(),
                    staged.metadata().compatibility(),
                    staged.metadata().allowedTools(),
                    staged.metadata().metadata(),
                    staged.metadata().instructions(),
                    staged.sha256(),
                    staged.contentLength(),
                    staged.files());
        }
    }

    public AssetView importPackage(
            CurrentActor actor,
            String namespace,
            UUID knowledgeSpaceId,
            KnowledgeClassification classification,
            long contentLength,
            InputStream content) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(classification, "classification");
        assets.requireSkillCreate(actor, knowledgeSpaceId);
        UUID packageId = UUID.randomUUID();
        SkillPackageStoragePort.StoredSkillPackage stored = null;
        UUID assetId = null;
        try (SkillPackageInspector.StagedSkillPackage staged =
                inspector.inspect(content, contentLength)) {
            try (InputStream packageContent = staged.open()) {
                stored = storage.put(
                        new SkillPackageStoragePort.SkillPackageWriteRequest(
                                actor.organizationId(),
                                packageId,
                                staged.contentLength(),
                                staged.sha256(),
                                Map.of("skill-name", staged.metadata().name())),
                        packageContent);
            }
            SkillPackageSpec spec = specification(staged, stored);
            AssetDraftInput draft = draft(spec, classification);
            assetId = assets.createValidatedSkillIdentity(
                    actor,
                    namespace,
                    spec.name(),
                    knowledgeSpaceId,
                    draft,
                    stored);
            return assets.projectCreated(actor, assetId);
        } catch (RuntimeException failure) {
            if (assetId == null) {
                deleteIfStored(stored, failure);
            }
            throw failure;
        } catch (IOException failure) {
            deleteIfStored(stored, failure);
            throw new BusinessUnavailableException(
                    "skill.package-staging-unavailable",
                    "The Skill package could not be staged",
                    failure);
        }
    }

    public AssetView replacePackage(
            CurrentActor actor,
            UUID assetId,
            long expectedLockVersion,
            long contentLength,
            InputStream content) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(assetId, "assetId");
        assets.requireSkillEdit(actor, assetId);
        AssetView current = assets.get(actor, assetId);
        if (current.type() != AssetType.SKILL || current.draft() == null) {
            throw new AssetNotFoundException();
        }
        KnowledgeClassification classification = KnowledgeClassification.valueOf(
                current.draft().classification());
        SkillPackageStoragePort.StoredSkillPackage stored = null;
        SkillDraftReplacement replacement = null;
        try (SkillPackageInspector.StagedSkillPackage staged =
                inspector.inspect(content, contentLength)) {
            try (InputStream packageContent = staged.open()) {
                stored = storage.put(
                        new SkillPackageStoragePort.SkillPackageWriteRequest(
                                actor.organizationId(),
                                UUID.randomUUID(),
                                staged.contentLength(),
                                staged.sha256(),
                                Map.of("skill-name", staged.metadata().name())),
                        packageContent);
            }
            SkillPackageSpec spec = specification(staged, stored);
            replacement = assets.replaceValidatedSkillDraft(
                    actor,
                    assetId,
                    expectedLockVersion,
                    draft(spec, classification),
                    stored);
        } catch (RuntimeException failure) {
            if (replacement == null) {
                deleteIfStored(stored, failure);
            }
            throw failure;
        } catch (IOException failure) {
            deleteIfStored(stored, failure);
            throw new BusinessUnavailableException(
                    "skill.package-staging-unavailable",
                    "The Skill package could not be staged",
                    failure);
        }
        cleanupAfterCommit(replacement.supersessionId());
        return replacement.asset();
    }

    private SkillPackageSpec specification(
            SkillPackageInspector.StagedSkillPackage staged,
            SkillPackageStoragePort.StoredSkillPackage stored) {
        return new SkillPackageSpec(
                staged.metadata().name(),
                staged.metadata().description(),
                staged.metadata().license(),
                staged.metadata().compatibility(),
                staged.metadata().allowedTools(),
                staged.metadata().metadata(),
                new SkillPackageSpec.Artifact(
                        stored.sha256(), stored.contentLength(), stored.mediaType()),
                staged.files());
    }

    private AssetDraftInput draft(
            SkillPackageSpec spec, KnowledgeClassification classification) {
        return new AssetDraftInput(
                spec.name(),
                spec.description(),
                classification.name(),
                SkillPackageProfile.SCHEMA_VERSION,
                json.writeValueAsString(spec));
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
            SkillPackageStoragePort.StoredSkillPackage stored, Throwable failure) {
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
