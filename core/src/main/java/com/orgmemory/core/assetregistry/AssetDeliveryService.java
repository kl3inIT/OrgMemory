package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.workinstructionrelationcontract.WorkInstructionRelationResolver;
import com.orgmemory.core.organization.CurrentActor;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Canonical read-only delivery boundary shared by REST and MCP adapters.
 */
@Service
public class AssetDeliveryService {

    private static final Logger log =
            LoggerFactory.getLogger(AssetDeliveryService.class);

    private final AssetRegistryService assets;
    private final CapabilityPackService packs;
    private final WorkInstructionRelationResolver workInstructions;

    AssetDeliveryService(
            AssetRegistryService assets,
            CapabilityPackService packs,
            WorkInstructionRelationResolver workInstructions) {
        this.assets = assets;
        this.packs = packs;
        this.workInstructions = workInstructions;
    }

    public List<AssetRecommendation> search(
            CurrentActor actor, String query, AssetType type) {
        List<AssetRecommendation> result =
                assets.recommend(actor, query, type);
        log.info(
                "asset_delivery action=search organization={} actor={} result_count={}",
                actor.organizationId(),
                actor.userId(),
                result.size());
        return result;
    }

    public AssetDeliveryRelease get(CurrentActor actor, UUID assetId) {
        AssetDeliveryRelease result = AssetDeliveryRelease.from(
                assets.latestReleaseForUse(actor, assetId));
        audit(actor, "get_asset", result.assetId(), result.releaseId());
        return result;
    }

    public AssetDeliveryRelease getRelease(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        AssetDeliveryRelease result = AssetDeliveryRelease.from(
                assets.releaseForUse(actor, assetId, releaseId));
        audit(actor, "get_asset_release", assetId, releaseId);
        return result;
    }

    public CapabilityPackDefinition getPack(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        CapabilityPackDefinition result =
                packs.describe(actor, assetId, releaseId);
        audit(actor, "get_capability_pack", assetId, releaseId);
        return result;
    }

    public void auditPromptRender(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        audit(actor, "render_prompt", assetId, releaseId);
    }

    public AssetRelationResolution resolveRelations(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        AssetConsumptionRelease release =
                assets.releaseForUse(actor, assetId, releaseId);
        if (release.type() == AssetType.CAPABILITY_PACK) {
            CapabilityPackDefinition pack =
                    packs.describe(actor, assetId, releaseId);
            AssetRelationResolution result = new AssetRelationResolution(
                    assetId,
                    releaseId,
                    pack.accessGap(),
                    pack.items().stream()
                            .map(item -> new AssetRelationResolution.Relation(
                                    item.key(),
                                    item.kind(),
                                    item.resourceId(),
                                    item.pinnedVersionId(),
                                    item.title(),
                                    item.versionLabel(),
                                    item.required()))
                            .toList());
            audit(actor, "resolve_asset_relations", assetId, releaseId);
            return result;
        }
        if (release.type() != AssetType.WORK_INSTRUCTION) {
            AssetRelationResolution result = new AssetRelationResolution(
                    assetId, releaseId, false, List.of());
            audit(actor, "resolve_asset_relations", assetId, releaseId);
            return result;
        }

        var resolution = workInstructions.resolveRelations(actor, release);
        AssetRelationResolution result = new AssetRelationResolution(
                assetId,
                releaseId,
                resolution.accessGap(),
                resolution.relations().stream()
                        .map(relation -> new AssetRelationResolution.Relation(
                                relation.key(),
                                relation.kind(),
                                relation.resourceId(),
                                relation.pinnedVersionId(),
                                relation.title(),
                                relation.versionLabel(),
                                relation.required()))
                        .toList());
        audit(actor, "resolve_asset_relations", assetId, releaseId);
        return result;
    }

    private static void audit(
            CurrentActor actor,
            String action,
            UUID assetId,
            UUID releaseId) {
        log.info(
                "asset_delivery action={} organization={} actor={} asset={} release={}",
                action,
                actor.organizationId(),
                actor.userId(),
                assetId,
                releaseId);
    }
}
