package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.organization.CurrentActor;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceQueryService {

    private final SourceObjectRepository sources;
    private final SourceRevisionRepository revisions;
    private final SourceEmbeddingProfileDirectory embeddingProfiles;
    private final SourceVisibilityPort visibility;
    private final SourceActionAuthorizationPort actions;

    SourceQueryService(
            SourceObjectRepository sources,
            SourceRevisionRepository revisions,
            SourceEmbeddingProfileDirectory embeddingProfiles,
            SourceVisibilityPort visibility,
            SourceActionAuthorizationPort actions) {
        this.sources = sources;
        this.revisions = revisions;
        this.embeddingProfiles = embeddingProfiles;
        this.visibility = visibility;
        this.actions = actions;
    }

    @Transactional(readOnly = true)
    public List<SourceSummary> listOwn(CurrentActor actor) {
        Objects.requireNonNull(actor, "actor");
        Set<UUID> contentVisible = Set.copyOf(visibility.visibleSourceObjectIds(actor));
        return summaries(
                actor.organizationId(),
                sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        actor.organizationId(), actor.userId()),
                contentVisible,
                actions.deletableKnowledgeAssetIds(actor));
    }

    @Transactional(readOnly = true)
    public List<SourceSummary> listVisible(CurrentActor actor) {
        Objects.requireNonNull(actor, "actor");
        Set<UUID> sourceIds = new LinkedHashSet<>();
        sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        actor.organizationId(), actor.userId())
                .forEach(source -> sourceIds.add(source.getId()));

        Set<UUID> contentVisible = Set.copyOf(visibility.visibleSourceObjectIds(actor));
        sourceIds.addAll(contentVisible);
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        return summaries(
                actor.organizationId(),
                sources.findAllByOrganizationIdAndIdInOrderByUpdatedAtDesc(
                        actor.organizationId(), sourceIds),
                contentVisible,
                actions.deletableKnowledgeAssetIds(actor));
    }

    private List<SourceSummary> summaries(
            UUID organizationId,
            List<SourceObject> visibleSources,
            Set<UUID> contentVisible,
            Set<UUID> deletableAssetIds) {
        visibleSources = visibleSources.stream()
                .filter(source -> source.getStatus() == SourceObjectStatus.ACTIVE)
                .toList();
        if (visibleSources.isEmpty()) {
            return List.of();
        }
        Map<UUID, SourceRevision> revisionById = new LinkedHashMap<>();
        revisions.findAllById(visibleSources.stream()
                        .map(SourceObject::getLatestRevisionId)
                        .filter(Objects::nonNull)
                        .toList())
                .forEach(revision -> revisionById.put(revision.getId(), revision));
        Map<UUID, SourceEmbeddingProfileView> profileById = new LinkedHashMap<>();
        return visibleSources.stream()
                .map(source -> {
                    SourceRevision revision = Objects.requireNonNull(
                            revisionById.get(source.getLatestRevisionId()),
                            "Source latest revision was not found");
                    SourceEmbeddingProfileView profile = revision.getEmbeddingProfileId() == null
                            ? null
                            : profileById.computeIfAbsent(
                                    revision.getEmbeddingProfileId(),
                                    profileId -> embeddingProfiles.get(organizationId, profileId));
                    return summary(
                            source,
                            revision,
                            profile,
                            contentVisible.contains(source.getId()),
                            deletableAssetIds.contains(revision.getKnowledgeAssetId()));
                })
                .toList();
    }

    static SourceSummary summary(
            SourceObject source,
            SourceRevision revision,
            SourceEmbeddingProfileView embeddingProfile) {
        return summary(source, revision, embeddingProfile, false, false);
    }

    static SourceSummary summary(
            SourceObject source,
            SourceRevision revision,
            SourceEmbeddingProfileView embeddingProfile,
            boolean contentAuthorized,
            boolean deleteAuthorized) {
        boolean ready = revision.getStatus() == SourceRevisionStatus.READY;
        UUID knowledgeAssetId = revision.getKnowledgeAssetId();
        return new SourceSummary(
                source.getId(),
                source.getTitle(),
                source.getSourceSystem(),
                source.getAclAuthority(),
                revision.getStatus(),
                source.getClassification(),
                revision.getFileName(),
                revision.getMediaType(),
                revision.getContentLength(),
                revision.getFailureCode(),
                revision.getFailureMessage(),
                knowledgeAssetId,
                ready && knowledgeAssetId != null && contentAuthorized,
                ready
                        && knowledgeAssetId != null
                        && SourceObject.NATIVE_UPLOAD_SYSTEM.equals(source.getSourceSystem())
                        && deleteAuthorized,
                embeddingProfile == null ? null : embeddingProfile.profileKey(),
                embeddingProfile == null ? null : embeddingProfile.provider(),
                embeddingProfile == null ? null : embeddingProfile.model(),
                revision.getEmbeddingDimensions(),
                source.getCreatedAt(),
                revision.getUpdatedAt());
    }
}
