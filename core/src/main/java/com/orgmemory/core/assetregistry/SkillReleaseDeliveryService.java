package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetIdentity;
import com.orgmemory.core.assetregistry.api.AssetIdentityQuery;
import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseContent;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseDeliveryQuery;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseDescriptor;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageArtifact;
import com.orgmemory.core.assetregistry.skillstorage.SkillPackageStoragePort;
import com.orgmemory.core.organization.CurrentActor;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
class SkillReleaseDeliveryService implements SkillReleaseDeliveryQuery {

    private static final Pattern COORDINATE =
            Pattern.compile("[a-z0-9]+(?:[._-][a-z0-9]+)*");

    private final AssetRegistryService assets;
    private final AssetIdentityQuery identities;
    private final AssetReleaseRepository releases;
    private final AssetPayloadReferenceRepository references;
    private final SkillPackageStoragePort storage;

    SkillReleaseDeliveryService(
            AssetRegistryService assets,
            AssetIdentityQuery identities,
            AssetReleaseRepository releases,
            AssetPayloadReferenceRepository references,
            SkillPackageStoragePort storage) {
        this.assets = assets;
        this.identities = identities;
        this.releases = releases;
        this.references = references;
        this.storage = storage;
    }

    @Override
    public SkillReleaseDescriptor describe(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        return resolve(actor, assetId, releaseId).descriptor();
    }

    @Override
    public SkillReleaseDescriptor describe(
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
        AssetRelease release = releases
                .findByAssetIdAndOrganizationIdAndVersionLabel(
                        asset.id(), actor.organizationId(), versionLabel)
                .orElseThrow(AssetNotFoundException::new);
        return describe(actor, asset.id(), release.getId());
    }

    @Override
    public SkillReleaseContent open(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        ResolvedRelease resolved = resolve(actor, assetId, releaseId);
        SkillPackageStoragePort.StoredSkillPackageContent content;
        try {
            content = storage.open(resolved.reference().getReferenceValue());
        } catch (RuntimeException unavailable) {
            throw new AssetUnavailableException(
                    "The Skill package is temporarily unavailable", unavailable);
        }
        try {
            verifyStored(resolved.reference(), content.metadata());
            return new SkillReleaseContent(
                    resolved.descriptor(), content.content());
        } catch (RuntimeException invalid) {
            try {
                content.close();
            } catch (Exception closeFailure) {
                invalid.addSuppressed(closeFailure);
            }
            throw invalid;
        }
    }

    private ResolvedRelease resolve(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        Objects.requireNonNull(actor, "actor");
        AssetConsumptionRelease release =
                assets.releaseForUse(actor, assetId, releaseId, AssetType.SKILL);
        AssetPayloadReference reference = references
                .findByReleaseIdAndOrganizationId(
                        releaseId, actor.organizationId())
                .orElseThrow(() -> new AssetUnavailableException(
                        "The Skill release package is unavailable"));
        if (!reference.isBlobReference()) {
            throw new AssetUnavailableException(
                    "The Skill release package metadata is inconsistent");
        }
        SkillReleaseDescriptor descriptor = new SkillReleaseDescriptor(
                release,
                new SkillPackageArtifact(
                        reference.getDigest(),
                        reference.getContentLength(),
                        reference.getMediaType()));
        return new ResolvedRelease(descriptor, reference);
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

    private static String normalizeCoordinate(String value, String field) {
        String normalized = Objects.requireNonNull(value, field)
                .strip()
                .toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()
                || normalized.length() > 128
                || !COORDINATE.matcher(normalized).matches()) {
            throw new AssetNotFoundException();
        }
        return normalized;
    }

    private record ResolvedRelease(
            SkillReleaseDescriptor descriptor,
            AssetPayloadReference reference) {
    }
}
