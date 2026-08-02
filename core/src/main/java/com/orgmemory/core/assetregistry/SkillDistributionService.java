package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetIdentity;
import com.orgmemory.core.assetregistry.api.AssetIdentityQuery;
import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.organization.CurrentActor;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Canonical authenticated Skill distribution boundary.
 */
@Service
public class SkillDistributionService {

    private static final Logger log =
            LoggerFactory.getLogger(SkillDistributionService.class);
    private static final Pattern COORDINATE =
            Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    private final AssetRegistryService assets;
    private final AssetIdentityQuery identities;
    private final AssetReleaseRepository releaseRepository;
    private final AssetPayloadReferenceRepository references;
    private final SkillPackageSpecReader specs;
    private final SkillPackageStoragePort storage;

    SkillDistributionService(
            AssetRegistryService assets,
            AssetIdentityQuery identities,
            AssetReleaseRepository releaseRepository,
            AssetPayloadReferenceRepository references,
            SkillPackageSpecReader specs,
            SkillPackageStoragePort storage) {
        this.assets = assets;
        this.identities = identities;
        this.releaseRepository = releaseRepository;
        this.references = references;
        this.specs = specs;
        this.storage = storage;
    }

    public SkillInstallManifest manifest(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        ResolvedSkill resolved = resolve(actor, assetId, releaseId);
        audit(actor, "get_skill_manifest", assetId, releaseId);
        return resolved.manifest();
    }

    public SkillInstallManifest manifest(
            CurrentActor actor,
            String namespace,
            String slug,
            String version) {
        Objects.requireNonNull(actor, "actor");
        AssetIdentity asset = identities
                .findByCoordinate(
                        actor.organizationId(),
                        normalizeCoordinate(namespace, "namespace"),
                        normalizeCoordinate(slug, "slug"))
                .filter(value -> value.type() == AssetType.SKILL)
                .orElseThrow(AssetNotFoundException::new);
        String versionLabel;
        try {
            versionLabel = AssetRelease.validateVersionLabel(version);
        } catch (IllegalArgumentException | NullPointerException invalid) {
            throw new AssetNotFoundException(invalid);
        }
        AssetRelease release = releaseRepository
                .findByAssetIdAndOrganizationIdAndVersionLabel(
                        asset.id(),
                        actor.organizationId(),
                        versionLabel)
                .orElseThrow(AssetNotFoundException::new);
        return manifest(actor, asset.id(), release.getId());
    }

    public SkillPackageContent open(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        ResolvedSkill resolved = resolve(actor, assetId, releaseId);
        SkillPackageStoragePort.StoredSkillPackageContent content;
        try {
            content = storage.open(resolved.reference().getReferenceValue());
        } catch (RuntimeException unavailable) {
            throw new AssetUnavailableException(
                    "The Skill package is temporarily unavailable",
                    unavailable);
        }
        try {
            verifyStored(resolved.reference(), content.metadata());
            audit(actor, "download_skill_package", assetId, releaseId);
            return new SkillPackageContent(
                    resolved.manifest(),
                    fileName(resolved.manifest()),
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

    private ResolvedSkill resolve(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        Objects.requireNonNull(actor, "actor");
        AssetConsumptionRelease release =
                assets.releaseForUse(actor, assetId, releaseId, AssetType.SKILL);
        SkillPackageSpec spec;
        try {
            spec = specs.read(release.payload());
        } catch (RuntimeException invalid) {
            throw new AssetUnavailableException(
                    "The Skill release manifest is unavailable",
                    invalid);
        }
        AssetPayloadReference reference = references
                .findByReleaseIdAndOrganizationId(
                        releaseId, actor.organizationId())
                .orElseThrow(() -> new AssetUnavailableException(
                        "The Skill release package is unavailable"));
        verifyReference(spec, reference);
        SkillInstallManifest manifest = new SkillInstallManifest(
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
                                file.path(),
                                file.size(),
                                file.sha256()))
                        .toList());
        return new ResolvedSkill(manifest, reference);
    }

    private static void verifyReference(
            SkillPackageSpec spec,
            AssetPayloadReference reference) {
        if (!reference.isBlobReference()
                || !spec.artifact().sha256().equals(reference.getDigest())
                || spec.artifact().contentLength()
                        != reference.getContentLength()
                || !spec.artifact().mediaType()
                        .equals(reference.getMediaType())) {
            throw new AssetUnavailableException(
                    "The Skill release package metadata is inconsistent");
        }
    }

    private static void verifyStored(
            AssetPayloadReference reference,
            SkillPackageStoragePort.StoredSkillPackage stored) {
        if (!reference.getReferenceValue().equals(stored.objectKey())
                || !reference.getDigest().equals(stored.sha256())
                || reference.getContentLength() != stored.contentLength()
                || !reference.getMediaType().equals(stored.mediaType())) {
            throw new AssetUnavailableException(
                    "The stored Skill package failed its integrity check");
        }
    }

    private static String fileName(SkillInstallManifest manifest) {
        String version = manifest.version()
                .replaceAll("[^A-Za-z0-9._-]", "-");
        return manifest.slug() + "-" + version + ".zip";
    }

    private static String normalizeCoordinate(
            String value, String field) {
        String normalized = Objects.requireNonNull(value, field)
                .strip()
                .toLowerCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()
                || normalized.length() > 128
                || !COORDINATE.matcher(normalized).matches()) {
            throw new AssetNotFoundException();
        }
        return normalized;
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

    private record ResolvedSkill(
            SkillInstallManifest manifest,
            AssetPayloadReference reference) {
    }
}
