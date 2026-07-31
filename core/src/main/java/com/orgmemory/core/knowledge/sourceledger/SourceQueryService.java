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

    SourceQueryService(
            SourceObjectRepository sources,
            SourceRevisionRepository revisions,
            SourceEmbeddingProfileDirectory embeddingProfiles,
            SourceVisibilityPort visibility) {
        this.sources = sources;
        this.revisions = revisions;
        this.embeddingProfiles = embeddingProfiles;
        this.visibility = visibility;
    }

    @Transactional(readOnly = true)
    public List<SourceSummary> listOwn(CurrentActor actor) {
        Objects.requireNonNull(actor, "actor");
        return summaries(
                actor.organizationId(),
                sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        actor.organizationId(), actor.userId()));
    }

    @Transactional(readOnly = true)
    public List<SourceSummary> listVisible(CurrentActor actor) {
        Objects.requireNonNull(actor, "actor");
        Set<UUID> sourceIds = new LinkedHashSet<>();
        sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        actor.organizationId(), actor.userId())
                .forEach(source -> sourceIds.add(source.getId()));

        sourceIds.addAll(visibility.visibleSourceObjectIds(actor));
        if (sourceIds.isEmpty()) {
            return List.of();
        }
        return summaries(
                actor.organizationId(),
                sources.findAllByOrganizationIdAndIdInOrderByUpdatedAtDesc(
                        actor.organizationId(), sourceIds));
    }

    private List<SourceSummary> summaries(UUID organizationId, List<SourceObject> visibleSources) {
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
                    return summary(source, revision, profile);
                })
                .toList();
    }

    static SourceSummary summary(
            SourceObject source,
            SourceRevision revision,
            SourceEmbeddingProfileView embeddingProfile) {
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
                embeddingProfile == null ? null : embeddingProfile.profileKey(),
                embeddingProfile == null ? null : embeddingProfile.provider(),
                embeddingProfile == null ? null : embeddingProfile.model(),
                revision.getEmbeddingDimensions(),
                source.getCreatedAt(),
                revision.getUpdatedAt());
    }
}
