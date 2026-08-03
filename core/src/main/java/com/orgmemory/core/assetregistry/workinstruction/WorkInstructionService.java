package com.orgmemory.core.assetregistry.workinstruction;

import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.consumption.AssetReleaseUseQuery;
import com.orgmemory.core.assetregistry.workinstructioncontract.WorkInstructionOperations;
import com.orgmemory.core.assetregistry.workinstructioncontract.WorkInstructionView;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class WorkInstructionService implements WorkInstructionOperations {

    private final AssetReleaseUseQuery assets;
    private final WorkInstructionProfile profile;
    private final WorkInstructionAcknowledgementRepository acknowledgements;

    WorkInstructionService(
            AssetReleaseUseQuery assets,
            WorkInstructionProfile profile,
            WorkInstructionAcknowledgementRepository acknowledgements) {
        this.assets = assets;
        this.profile = profile;
        this.acknowledgements = acknowledgements;
    }

    @Transactional(readOnly = true)
    @Override
    public WorkInstructionView follow(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        AssetConsumptionRelease release = assets.workInstructionForUse(
                actor, assetId, releaseId);
        WorkInstructionAcknowledgement acknowledgement = acknowledgements
                .findByOrganizationIdAndReleaseIdAndActorUserId(
                        actor.organizationId(), releaseId, actor.userId())
                .orElse(null);
        return view(release, acknowledgement);
    }

    @Transactional
    @Override
    public WorkInstructionView acknowledge(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        Objects.requireNonNull(actor, "actor");
        AssetConsumptionRelease release = assets.workInstructionForUse(
                actor, assetId, releaseId);
        WorkInstructionAcknowledgement acknowledgement = acknowledgements
                .findByOrganizationIdAndReleaseIdAndActorUserId(
                        actor.organizationId(), releaseId, actor.userId())
                .orElseGet(() -> insert(actor, release));
        return view(release, acknowledgement);
    }

    private WorkInstructionAcknowledgement insert(
            CurrentActor actor,
            AssetConsumptionRelease release) {
        Instant timestamp = Instant.now();
        acknowledgements.insertIfAbsent(
                UUID.randomUUID(),
                actor.organizationId(),
                release.assetId(),
                release.releaseId(),
                release.digest(),
                actor.userId(),
                timestamp);
        return acknowledgements
                .findByOrganizationIdAndReleaseIdAndActorUserId(
                        actor.organizationId(),
                        release.releaseId(),
                        actor.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "Work Instruction acknowledgement was not persisted"));
    }

    private WorkInstructionView view(
            AssetConsumptionRelease release,
            WorkInstructionAcknowledgement acknowledgement) {
        return new WorkInstructionView(
                release.assetId(),
                release.releaseId(),
                release.digest(),
                release.title(),
                release.versionLabel(),
                profile.parse(release.payload()),
                acknowledgement != null,
                acknowledgement == null ? null : acknowledgement.acknowledgedAt());
    }
}
