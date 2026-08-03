package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseContent;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseDeliveryQuery;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseDescriptor;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageArtifact;
import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Canonical authenticated Skill distribution boundary. */
@Service
public class SkillDistributionService {

    private static final Logger log =
            LoggerFactory.getLogger(SkillDistributionService.class);

    private final SkillReleaseDeliveryQuery deliveries;
    private final SkillPackageSpecReader specs;

    SkillDistributionService(
            SkillReleaseDeliveryQuery deliveries,
            SkillPackageSpecReader specs) {
        this.deliveries = deliveries;
        this.specs = specs;
    }

    public SkillInstallManifest manifest(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        SkillReleaseDescriptor descriptor =
                deliveries.describe(actor, assetId, releaseId);
        SkillInstallManifest manifest = manifest(descriptor);
        audit(actor, "get_skill_manifest", assetId, releaseId);
        return manifest;
    }

    public SkillInstallManifest manifest(
            CurrentActor actor,
            String namespace,
            String slug,
            String version) {
        SkillReleaseDescriptor descriptor =
                deliveries.describe(actor, namespace, slug, version);
        SkillInstallManifest manifest = manifest(descriptor);
        audit(
                actor,
                "get_skill_manifest",
                descriptor.release().assetId(),
                descriptor.release().releaseId());
        return manifest;
    }

    public SkillPackageContent open(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        SkillReleaseContent content = deliveries.open(actor, assetId, releaseId);
        try {
            SkillInstallManifest manifest = manifest(content.descriptor());
            audit(actor, "download_skill_package", assetId, releaseId);
            return new SkillPackageContent(
                    manifest,
                    fileName(manifest),
                    content.content());
        } catch (RuntimeException invalid) {
            try {
                content.close();
            } catch (Exception closeFailure) {
                invalid.addSuppressed(closeFailure);
            }
            throw invalid;
        }
    }

    private SkillInstallManifest manifest(SkillReleaseDescriptor descriptor) {
        AssetConsumptionRelease release = descriptor.release();
        SkillPackageSpec spec;
        try {
            spec = specs.read(release.payload());
        } catch (RuntimeException invalid) {
            throw new AssetUnavailableException(
                    "The Skill release manifest is unavailable", invalid);
        }
        verifyReference(spec, descriptor.artifact());
        return new SkillInstallManifest(
                release.assetId(),
                release.releaseId(),
                release.namespace(),
                release.slug(),
                release.namespace() + "/" + release.slug(),
                release.versionLabel(),
                release.publicationMode(),
                release.title(),
                release.summary(),
                release.digest(),
                spec.artifact().sha256(),
                spec.artifact().contentLength(),
                spec.artifact().mediaType(),
                spec.license(),
                spec.compatibility(),
                spec.allowedTools(),
                spec.metadata(),
                spec.files().stream()
                        .map(file -> new SkillInstallManifest.File(
                                file.path(), file.size(), file.sha256()))
                        .toList());
    }

    private static void verifyReference(
            SkillPackageSpec spec, SkillPackageArtifact artifact) {
        if (!spec.artifact().sha256().equals(artifact.sha256())
                || spec.artifact().contentLength() != artifact.contentLength()
                || !spec.artifact().mediaType().equals(artifact.mediaType())) {
            throw new AssetUnavailableException(
                    "The Skill release package metadata is inconsistent");
        }
    }

    private static String fileName(SkillInstallManifest manifest) {
        String version = manifest.version()
                .replaceAll("[^A-Za-z0-9._-]", "-");
        return manifest.slug() + "-" + version + ".zip";
    }

    private static void audit(
            CurrentActor actor,
            String action,
            UUID assetId,
            UUID releaseId) {
        log.info(
                "skill_distribution action={} organization={} actor={} asset={} release={}",
                action,
                actor.organizationId(),
                actor.userId(),
                assetId,
                releaseId);
    }
}
