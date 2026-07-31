package com.orgmemory.core.assetregistry;

import com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogService;
import com.orgmemory.core.organization.CurrentActor;
import java.util.ArrayList;
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
    private final WorkInstructionProfile workInstructions;
    private final KnowledgeCatalogService knowledge;

    AssetDeliveryService(
            AssetRegistryService assets,
            CapabilityPackService packs,
            WorkInstructionProfile workInstructions,
            KnowledgeCatalogService knowledge) {
        this.assets = assets;
        this.packs = packs;
        this.workInstructions = workInstructions;
        this.knowledge = knowledge;
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

        WorkInstructionSpec spec = workInstructions.parse(release.payload());
        List<AssetRelationResolution.Relation> visible = new ArrayList<>();
        boolean accessGap = false;
        for (WorkInstructionSpec.Step step : spec.steps()) {
            for (UUID relatedAssetId : step.relatedAssetIds()) {
                try {
                    AssetConsumptionRelease related =
                            assets.latestReleaseForUse(actor, relatedAssetId);
                    visible.add(new AssetRelationResolution.Relation(
                            step.key(),
                            "REGISTRY_RELEASE",
                            related.assetId(),
                            related.releaseId(),
                            related.title(),
                            related.versionLabel(),
                            false));
                } catch (AssetNotFoundException | AssetUnavailableException denied) {
                    accessGap = true;
                }
            }
            for (UUID knowledgeVersionId : step.relatedKnowledgeVersionIds()) {
                var related = knowledge.findVersionVisible(
                        actor, knowledgeVersionId);
                if (related.isPresent()) {
                    var value = related.get();
                    visible.add(new AssetRelationResolution.Relation(
                            step.key(),
                            "KNOWLEDGE",
                            value.knowledgeAssetId(),
                            value.knowledgeVersionId(),
                            value.title(),
                            Long.toString(value.versionNumber()),
                            false));
                } else {
                    accessGap = true;
                }
            }
        }
        AssetRelationResolution result = new AssetRelationResolution(
                assetId, releaseId, accessGap, visible);
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
