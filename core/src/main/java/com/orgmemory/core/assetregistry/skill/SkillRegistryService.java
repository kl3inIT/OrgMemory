package com.orgmemory.core.assetregistry.skill;

import com.orgmemory.core.assetregistry.skillpackage.SkillPackageArtifact;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageAssetCommand;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageUpload;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.error.BusinessUnavailableException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Service
class SkillRegistryService implements SkillPackageOperations {

    private final SkillPackageInspector inspector;
    private final SkillPackageAssetCommand packages;
    private final ObjectMapper json = JsonMapper.builder().build();

    SkillRegistryService(
            SkillPackageInspector inspector,
            SkillPackageAssetCommand packages) {
        this.inspector = inspector;
        this.packages = packages;
    }

    @Override
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
                    staged.files().stream()
                            .map(file -> new SkillPackageInspection.FileEntry(
                                    file.path(), file.size(), file.sha256()))
                            .toList());
        }
    }

    @Override
    public UUID importPackage(
            CurrentActor actor,
            String namespace,
            UUID knowledgeSpaceId,
            KnowledgeClassification classification,
            long contentLength,
            InputStream content) {
        return importPackage(
                actor,
                namespace,
                knowledgeSpaceId,
                classification,
                contentLength,
                content,
                null);
    }

    UUID importPackage(
            CurrentActor actor,
            String namespace,
            UUID knowledgeSpaceId,
            KnowledgeClassification classification,
            long contentLength,
            InputStream content,
            SkillPackageSpec.Origin origin) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(classification, "classification");
        packages.requireCreate(actor, knowledgeSpaceId);
        try (SkillPackageInspector.StagedSkillPackage staged =
                        inspector.inspect(content, contentLength);
                InputStream packageContent = staged.open()) {
            SkillPackageArtifact artifact = new SkillPackageArtifact(
                    staged.sha256(),
                    staged.contentLength(),
                    SkillPackageArtifact.ZIP_MEDIA_TYPE);
            SkillPackageSpec spec = specification(staged, artifact, origin);
            return packages.importPackage(
                    actor,
                    namespace,
                    knowledgeSpaceId,
                    classification,
                    upload(spec, artifact, packageContent));
        } catch (IOException failure) {
            throw new BusinessUnavailableException(
                    "skill.package-staging-unavailable",
                    "The Skill package could not be staged",
                    failure);
        }
    }

    @Override
    public UUID replacePackage(
            CurrentActor actor,
            UUID assetId,
            long expectedLockVersion,
            long contentLength,
            InputStream content) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(assetId, "assetId");
        packages.requireEdit(actor, assetId);
        try (SkillPackageInspector.StagedSkillPackage staged =
                        inspector.inspect(content, contentLength);
                InputStream packageContent = staged.open()) {
            SkillPackageArtifact artifact = new SkillPackageArtifact(
                    staged.sha256(),
                    staged.contentLength(),
                    SkillPackageArtifact.ZIP_MEDIA_TYPE);
            SkillPackageSpec spec = specification(staged, artifact, null);
            return packages.replacePackage(
                    actor,
                    assetId,
                    expectedLockVersion,
                    upload(spec, artifact, packageContent));
        } catch (IOException failure) {
            throw new BusinessUnavailableException(
                    "skill.package-staging-unavailable",
                    "The Skill package could not be staged",
                    failure);
        }
    }

    private SkillPackageSpec specification(
            SkillPackageInspector.StagedSkillPackage staged,
            SkillPackageArtifact artifact,
            SkillPackageSpec.Origin origin) {
        return new SkillPackageSpec(
                staged.metadata().name(),
                staged.metadata().description(),
                staged.metadata().license(),
                staged.metadata().compatibility(),
                staged.metadata().allowedTools(),
                staged.metadata().metadata(),
                origin,
                new SkillPackageSpec.Artifact(
                        artifact.sha256(),
                        artifact.contentLength(),
                        artifact.mediaType()),
                staged.files());
    }

    private SkillPackageUpload upload(
            SkillPackageSpec spec,
            SkillPackageArtifact artifact,
            InputStream content) {
        String payload;
        try {
            payload = json.writeValueAsString(spec);
        } catch (JacksonException failure) {
            throw new BusinessUnavailableException(
                    "skill.package-staging-unavailable",
                    "The Skill package could not be staged",
                    failure);
        }
        return new SkillPackageUpload(
                UUID.randomUUID(),
                spec.name(),
                spec.name(),
                spec.description(),
                SkillPackageProfile.SCHEMA_VERSION,
                payload,
                artifact,
                Map.of("skill-name", spec.name()),
                content);
    }
}
