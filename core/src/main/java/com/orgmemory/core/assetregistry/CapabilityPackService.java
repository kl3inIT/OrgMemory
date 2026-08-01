package com.orgmemory.core.assetregistry;

import com.orgmemory.core.knowledge.asset.KnowledgeCatalogItem;
import com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogService;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapabilityPackService {

    private final AssetRegistryService assets;
    private final CapabilityPackProfile profile;
    private final KnowledgeCatalogService knowledge;
    private final PackAssignmentRepository assignments;
    private final PackProgressRepository progress;

    CapabilityPackService(
            AssetRegistryService assets,
            CapabilityPackProfile profile,
            KnowledgeCatalogService knowledge,
            PackAssignmentRepository assignments,
            PackProgressRepository progress) {
        this.assets = assets;
        this.profile = profile;
        this.knowledge = knowledge;
        this.assignments = assignments;
        this.progress = progress;
    }

    @Transactional
    public PackJourney start(
            CurrentActor actor,
            UUID packAssetId,
            UUID packReleaseId) {
        AssetConsumptionRelease release = packRelease(
                actor, packAssetId, packReleaseId);
        PackAssignment assignment = assignments
                .findByOrganizationIdAndPackReleaseIdAndActorUserId(
                        actor.organizationId(), packReleaseId, actor.userId())
                .orElseGet(() -> insertAssignment(actor, release));
        return journey(actor, release, assignment);
    }

    @Transactional(readOnly = true)
    public PackJourney get(
            CurrentActor actor,
            UUID packAssetId,
            UUID packReleaseId) {
        AssetConsumptionRelease release = packRelease(
                actor, packAssetId, packReleaseId);
        PackAssignment assignment = assignments
                .findByOrganizationIdAndPackReleaseIdAndActorUserId(
                        actor.organizationId(), packReleaseId, actor.userId())
                .orElseThrow(AssetNotFoundException::new);
        return journey(actor, release, assignment);
    }

    @Transactional(readOnly = true)
    public CapabilityPackDefinition describe(
            CurrentActor actor,
            UUID packAssetId,
            UUID packReleaseId) {
        AssetConsumptionRelease release =
                packRelease(actor, packAssetId, packReleaseId);
        CapabilityPackSpec spec = profile.parse(release.payload());
        List<CapabilityPackDefinition.Item> visibleItems = new ArrayList<>();
        boolean accessGap = false;
        for (int index = 0; index < spec.items().size(); index++) {
            CapabilityPackSpec.Item item = spec.items().get(index);
            Optional<CapabilityPackDefinition.Item> resolved =
                    resolveDefinition(actor, item, index + 1);
            if (resolved.isPresent()) {
                visibleItems.add(resolved.get());
            } else {
                accessGap = true;
            }
        }
        return new CapabilityPackDefinition(
                release.assetId(),
                release.releaseId(),
                release.digest(),
                release.title(),
                release.versionLabel(),
                spec.purpose(),
                spec.audience(),
                spec.prerequisites(),
                spec.expectedOutcome(),
                spec.completionCriteria(),
                spec.reviewDate(),
                spec.owner(),
                accessGap,
                visibleItems);
    }

    @Transactional
    public PackJourney setItemCompleted(
            CurrentActor actor,
            UUID packAssetId,
            UUID packReleaseId,
            String itemKey,
            boolean completed) {
        AssetConsumptionRelease release = packRelease(
                actor, packAssetId, packReleaseId);
        CapabilityPackSpec spec = profile.parse(release.payload());
        CapabilityPackSpec.Item item = spec.items().stream()
                .filter(candidate -> candidate.key().equals(itemKey))
                .findFirst()
                .orElseThrow(AssetNotFoundException::new);
        if (resolve(actor, item, 0).isEmpty()) {
            throw new AssetNotFoundException();
        }
        PackAssignment existing = assignments
                .findByOrganizationIdAndPackReleaseIdAndActorUserId(
                        actor.organizationId(), packReleaseId, actor.userId())
                .orElseGet(() -> insertAssignment(actor, release));
        PackAssignment assignment = assignments
                .findForUpdateByIdAndOrganizationIdAndActorUserId(
                        existing.getId(), actor.organizationId(), actor.userId())
                .orElseThrow(AssetNotFoundException::new);
        if (assignment.getStatus() == PackAssignmentStatus.COMPLETED && !completed) {
            throw new AssetConflictException(
                    "A completed Pack assignment cannot be reopened");
        }
        Instant now = Instant.now();
        progress.upsert(
                UUID.randomUUID(),
                actor.organizationId(),
                assignment.getId(),
                item.key(),
                completed,
                now);
        Map<String, PackProgress> byKey = progressByKey(actor, assignment);
        boolean allRequiredComplete = spec.items().stream()
                .filter(CapabilityPackSpec.Item::required)
                .allMatch(required -> byKey.containsKey(required.key())
                        && byKey.get(required.key()).isCompleted()
                        && resolve(actor, required, 0).isPresent());
        if (allRequiredComplete) {
            assignment.complete(now);
            assignments.saveAndFlush(assignment);
        }
        return journey(actor, release, assignment);
    }

    private AssetConsumptionRelease packRelease(
            CurrentActor actor,
            UUID packAssetId,
            UUID packReleaseId) {
        return assets.releaseForUse(
                actor,
                packAssetId,
                packReleaseId,
                AssetType.CAPABILITY_PACK);
    }

    private PackAssignment insertAssignment(
            CurrentActor actor,
            AssetConsumptionRelease release) {
        Instant timestamp = Instant.now();
        assignments.insertIfAbsent(
                UUID.randomUUID(),
                actor.organizationId(),
                release.assetId(),
                release.releaseId(),
                release.digest(),
                actor.userId(),
                timestamp);
        return assignments
                .findByOrganizationIdAndPackReleaseIdAndActorUserId(
                        actor.organizationId(),
                        release.releaseId(),
                        actor.userId())
                .orElseThrow(() -> new IllegalStateException(
                        "Pack assignment was not persisted"));
    }

    private PackJourney journey(
            CurrentActor actor,
            AssetConsumptionRelease release,
            PackAssignment assignment) {
        CapabilityPackSpec spec = profile.parse(release.payload());
        Map<String, PackProgress> byKey = progressByKey(actor, assignment);
        List<PackJourney.Item> visibleItems = new ArrayList<>();
        boolean accessGap = false;
        for (int index = 0; index < spec.items().size(); index++) {
            CapabilityPackSpec.Item item = spec.items().get(index);
            Optional<PackJourney.Item> resolved = resolve(actor, item, index + 1)
                    .map(value -> withProgress(value, byKey.get(item.key())));
            if (resolved.isPresent()) {
                visibleItems.add(resolved.get());
            } else {
                accessGap = true;
            }
        }
        int completed = (int) visibleItems.stream()
                .filter(PackJourney.Item::completed)
                .count();
        return new PackJourney(
                assignment.getId(),
                release.assetId(),
                release.releaseId(),
                release.digest(),
                release.title(),
                release.versionLabel(),
                spec.purpose(),
                spec.audience(),
                spec.expectedOutcome(),
                assignment.getStatus(),
                accessGap,
                completed,
                visibleItems,
                assignment.getStartedAt(),
                assignment.getCompletedAt());
    }

    private Map<String, PackProgress> progressByKey(
            CurrentActor actor,
            PackAssignment assignment) {
        Map<String, PackProgress> byKey = new HashMap<>();
        progress.findByOrganizationIdAndAssignmentIdOrderByItemKey(
                        actor.organizationId(), assignment.getId())
                .forEach(item -> byKey.put(item.getItemKey(), item));
        return byKey;
    }

    private Optional<PackJourney.Item> resolve(
            CurrentActor actor,
            CapabilityPackSpec.Item item,
            int order) {
        try {
            if (item.kind() == CapabilityPackSpec.ItemKind.REGISTRY_RELEASE) {
                AssetConsumptionRelease component = assets.releaseForUse(
                        actor, item.assetId(), item.releaseId());
                return Optional.of(new PackJourney.Item(
                        item.key(),
                        item.required(),
                        order,
                        component.type().name(),
                        component.assetId(),
                        component.releaseId(),
                        component.title(),
                        component.versionLabel(),
                        component.availability(),
                        false,
                        null));
            }
            Optional<KnowledgeCatalogItem> component = knowledge.findExactVisible(
                    actor, item.knowledgeAssetId(), item.knowledgeVersionId());
            return component.map(value -> new PackJourney.Item(
                    item.key(),
                    item.required(),
                    order,
                    "KNOWLEDGE",
                    value.knowledgeAssetId(),
                    value.knowledgeVersionId(),
                    value.title(),
                    Long.toString(value.versionNumber()),
                    AssetAvailability.AVAILABLE,
                    false,
                    null));
        } catch (AssetNotFoundException | AssetUnavailableException deniedOrUnavailable) {
            return Optional.empty();
        }
    }

    private Optional<CapabilityPackDefinition.Item> resolveDefinition(
            CurrentActor actor,
            CapabilityPackSpec.Item item,
            int order) {
        try {
            if (item.kind() == CapabilityPackSpec.ItemKind.REGISTRY_RELEASE) {
                AssetConsumptionRelease component = assets.releaseForUse(
                        actor, item.assetId(), item.releaseId());
                return Optional.of(new CapabilityPackDefinition.Item(
                        item.key(),
                        item.required(),
                        order,
                        component.type().name(),
                        component.assetId(),
                        component.releaseId(),
                        component.title(),
                        component.versionLabel(),
                        component.availability()));
            }
            Optional<KnowledgeCatalogItem> component = knowledge.findExactVisible(
                    actor, item.knowledgeAssetId(), item.knowledgeVersionId());
            return component.map(value -> new CapabilityPackDefinition.Item(
                    item.key(),
                    item.required(),
                    order,
                    "KNOWLEDGE",
                    value.knowledgeAssetId(),
                    value.knowledgeVersionId(),
                    value.title(),
                    Long.toString(value.versionNumber()),
                    AssetAvailability.AVAILABLE));
        } catch (AssetNotFoundException | AssetUnavailableException deniedOrUnavailable) {
            return Optional.empty();
        }
    }

    private static PackJourney.Item withProgress(
            PackJourney.Item item,
            PackProgress progress) {
        if (progress == null) {
            return item;
        }
        return new PackJourney.Item(
                item.key(),
                item.required(),
                item.order(),
                item.kind(),
                item.resourceId(),
                item.pinnedVersionId(),
                item.title(),
                item.versionLabel(),
                item.availability(),
                progress.isCompleted(),
                progress.getCompletedAt());
    }
}
