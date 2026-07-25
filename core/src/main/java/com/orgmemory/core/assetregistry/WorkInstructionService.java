package com.orgmemory.core.assetregistry;

import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkInstructionService {

    private final AssetRegistryService assets;
    private final WorkInstructionProfile profile;
    private final WorkInstructionAcknowledgementRepository acknowledgements;

    WorkInstructionService(
            AssetRegistryService assets,
            WorkInstructionProfile profile,
            WorkInstructionAcknowledgementRepository acknowledgements) {
        this.assets = assets;
        this.profile = profile;
        this.acknowledgements = acknowledgements;
    }

    @Transactional(readOnly = true)
    public WorkInstructionView follow(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        AssetConsumptionRelease release = assets.releaseForUse(
                actor, assetId, releaseId, AssetType.WORK_INSTRUCTION);
        WorkInstructionAcknowledgement acknowledgement = acknowledgements
                .findByOrganizationIdAndReleaseIdAndActorUserId(
                        actor.organizationId(), releaseId, actor.userId())
                .orElse(null);
        return view(release, acknowledgement);
    }

    @Transactional
    public WorkInstructionView acknowledge(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId) {
        Objects.requireNonNull(actor, "actor");
        AssetConsumptionRelease release = assets.releaseForUse(
                actor, assetId, releaseId, AssetType.WORK_INSTRUCTION);
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
