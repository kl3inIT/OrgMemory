package com.orgmemory.core.assetregistry.prompt;

import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.consumption.AssetReleaseUseQuery;
import com.orgmemory.core.assetregistry.promptcontract.PromptPreparationResult;
import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PromptPreparationService {

    private final AssetReleaseUseQuery releases;
    private final PromptTemplateRenderer renderer;

    PromptPreparationService(
            AssetReleaseUseQuery releases, PromptTemplateRenderer renderer) {
        this.releases = releases;
        this.renderer = renderer;
    }

    public PromptPreparationResult preparePrompt(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        AssetConsumptionRelease release =
                releases.promptTemplateForUse(actor, assetId, releaseId);
        PromptTemplateSpec spec = renderer.parse(release.payload());
        return new PromptPreparationResult(
                release.assetId(),
                release.releaseId(),
                release.digest(),
                spec.objective(),
                spec.audience(),
                spec.variables().stream()
                        .map(variable -> new PromptPreparationResult.Variable(
                                variable.name(),
                                PromptPreparationResult.VariableType.valueOf(
                                        variable.type().name()),
                                variable.required(),
                                variable.defaultValue(),
                                variable.sensitive(),
                                variable.pattern(),
                                variable.allowedValues()))
                        .toList(),
                spec.outputContract(),
                spec.knowledgeRequirements(),
                spec.knownLimitations());
    }
}
