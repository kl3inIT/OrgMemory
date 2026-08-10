package com.orgmemory.core.assetregistry.skill;

import com.orgmemory.core.organization.CurrentActor;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Per-user opt-in state for runtime-visible Skill Assets. */
@Service
class SkillActivationService implements SkillActivationOperations {

    private final AssetSkillActivationRepository activations;

    SkillActivationService(AssetSkillActivationRepository activations) {
        this.activations = activations;
    }

    @Transactional(readOnly = true)
    @Override
    public boolean isEnabled(CurrentActor actor, UUID assetId) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(assetId, "assetId");
        return activations.findByOrganizationIdAndAssetIdAndUserId(
                        actor.organizationId(), assetId, actor.userId())
                .map(AssetSkillActivation::isEnabled)
                .orElse(false);
    }

    @Transactional
    @Override
    public boolean setEnabled(CurrentActor actor, UUID assetId, boolean enabled) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(assetId, "assetId");
        AssetSkillActivation activation = activations
                .findByOrganizationIdAndAssetIdAndUserId(
                        actor.organizationId(), assetId, actor.userId())
                .orElseGet(() -> new AssetSkillActivation(
                        actor.organizationId(), assetId, actor.userId(), enabled));
        activation.setEnabled(enabled);
        return activations.save(activation).isEnabled();
    }
}
