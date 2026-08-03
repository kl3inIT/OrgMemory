package com.orgmemory.core.assetregistry.workinstruction;

import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.consumption.AssetReleaseUseQuery;
import com.orgmemory.core.assetregistry.workinstructioncontract.WorkInstructionSpec;
import com.orgmemory.core.assetregistry.workinstructionrelationcontract.WorkInstructionRelationResolver;
import com.orgmemory.core.assetregistry.workinstructionrelationcontract.WorkInstructionRelations;
import com.orgmemory.core.knowledge.catalog.KnowledgeCatalogQuery;
import com.orgmemory.core.organization.CurrentActor;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class WorkInstructionRelationService implements WorkInstructionRelationResolver {

    private final AssetReleaseUseQuery assets;
    private final WorkInstructionProfile workInstructions;
    private final KnowledgeCatalogQuery knowledge;

    WorkInstructionRelationService(
            AssetReleaseUseQuery assets,
            WorkInstructionProfile workInstructions,
            KnowledgeCatalogQuery knowledge) {
        this.assets = assets;
        this.workInstructions = workInstructions;
        this.knowledge = knowledge;
    }

    @Override
    public WorkInstructionRelations resolveRelations(
            CurrentActor actor, AssetConsumptionRelease authorizedRelease) {
        if (authorizedRelease.type() != AssetType.WORK_INSTRUCTION) {
            throw new AssetNotFoundException();
        }

        WorkInstructionSpec spec = workInstructions.parse(authorizedRelease.payload());
        List<WorkInstructionRelations.Relation> visible = new ArrayList<>();
        boolean accessGap = false;
        for (WorkInstructionSpec.Step step : spec.steps()) {
            for (UUID relatedAssetId : step.relatedAssetIds()) {
                try {
                    AssetConsumptionRelease related =
                            assets.latestReleaseForUse(actor, relatedAssetId);
                    visible.add(new WorkInstructionRelations.Relation(
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
                var related = knowledge.findVersionVisible(actor, knowledgeVersionId);
                if (related.isPresent()) {
                    var value = related.get();
                    visible.add(new WorkInstructionRelations.Relation(
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
        return new WorkInstructionRelations(accessGap, visible);
    }
}
