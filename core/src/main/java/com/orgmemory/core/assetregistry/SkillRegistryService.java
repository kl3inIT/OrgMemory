package com.orgmemory.core.assetregistry;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.error.BusinessUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
public class SkillRegistryService {

    private final SkillPackageInspector inspector;
    private final SkillPackageStoragePort storage;
    private final AssetRegistryService assets;
    private final ObjectMapper json = JsonMapper.builder().build();

    SkillRegistryService(
            SkillPackageInspector inspector,
            SkillPackageStoragePort storage,
            AssetRegistryService assets) {
        this.inspector = inspector;
        this.storage = storage;
        this.assets = assets;
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
            SkillPackageSpec spec = new SkillPackageSpec(
                    staged.metadata().name(),
                    staged.metadata().description(),
                    staged.metadata().license(),
                    staged.metadata().compatibility(),
                    staged.metadata().allowedTools(),
                    staged.metadata().metadata(),
                    new SkillPackageSpec.Artifact(
                            stored.sha256(),
                            stored.contentLength(),
                            stored.mediaType()),
                    staged.files());
            AssetDraftInput draft = new AssetDraftInput(
                    spec.name(),
                    spec.description(),
                    classification.name(),
                    SkillPackageProfile.SCHEMA_VERSION,
                    json.writeValueAsString(spec));
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
