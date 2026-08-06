package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrganizationProvenanceQuery;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceQueryService {

    private final SourceObjectRepository sources;
    private final SourceRevisionRepository revisions;
    private final SourceEmbeddingProfileDirectory embeddingProfiles;
    private final SourceKnowledgeSpacePort knowledgeSpaces;
    private final OrganizationProvenanceQuery provenance;
    private final SourceVisibilityPort visibility;
    private final SourceActionAuthorizationPort actions;

    SourceQueryService(
            SourceObjectRepository sources,
            SourceRevisionRepository revisions,
            SourceEmbeddingProfileDirectory embeddingProfiles,
            SourceKnowledgeSpacePort knowledgeSpaces,
            OrganizationProvenanceQuery provenance,
            SourceVisibilityPort visibility,
            SourceActionAuthorizationPort actions) {
        this.sources = sources;
        this.revisions = revisions;
        this.embeddingProfiles = embeddingProfiles;
        this.knowledgeSpaces = knowledgeSpaces;
        this.provenance = provenance;
        this.visibility = visibility;
        this.actions = actions;
    }

    @Transactional(readOnly = true)
    public List<SourceSummary> listOwn(CurrentActor actor) {
        Objects.requireNonNull(actor, "actor");
        return summaries(
                actor.organizationId(),
                sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        actor.organizationId(), actor.userId()),
                Set.of(),
                Set.of());
    }

    @Transactional(readOnly = true)
    public SourceSummaryPage listVisible(CurrentActor actor, SourceListCommand command) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(command, "command");
        int pageSize = Math.min(Math.max(command.pageSize(), 1), 60);
        SourceListCursor cursor = SourceListCursor.decode(command.cursor());
        String query = normalizeQuery(command.query());

        Set<UUID> contentVisible = Set.copyOf(visibility.visibleSourceObjectIds(actor));
        int maximumAuthorizedObjects = visibility.maximumAuthorizedObjects();
        Set<UUID> sourceIds = new LinkedHashSet<>();
        sourceIds.addAll(sources.findActiveOwnedIds(
                actor.organizationId(),
                actor.userId(),
                maximumAuthorizedObjects + 1));
        sourceIds.addAll(contentVisible);
        visibility.requireWithinMaximumAuthorizedObjects(sourceIds.size());
        if (sourceIds.isEmpty()) {
            return SourceSummaryPage.empty(pageSize);
        }

        List<SourceObject> overFetched = sources.findVisiblePage(
                actor.organizationId(),
                sourceIds,
                command.knowledgeSpaceId(),
                command.classification() == null ? null : command.classification().name(),
                command.status() == null ? null : command.status().name(),
                query,
                cursor == null ? null : cursor.updatedAt(),
                cursor == null ? null : cursor.sourceId(),
                pageSize + 1);
        boolean hasMore = overFetched.size() > pageSize;
        List<SourceObject> pageSources = hasMore
                ? overFetched.subList(0, pageSize)
                : overFetched;
        List<SourceSummary> items = summaries(
                actor.organizationId(),
                pageSources,
                contentVisible,
                actions.deletableKnowledgeAssetIds(actor));
        SourceObjectRepository.SourceListingCountProjection count = sources.countVisible(
                actor.organizationId(),
                sourceIds,
                command.knowledgeSpaceId(),
                command.classification() == null ? null : command.classification().name(),
                query);
        String nextCursor = hasMore && !items.isEmpty()
                ? SourceListCursor.encode(items.getLast().updatedAt(), items.getLast().id())
                : null;
        return new SourceSummaryPage(
                items,
                nextCursor,
                pageSize,
                count.getTotal(),
                SourceStatusCounts.from(count));
    }

    private static String normalizeQuery(String query) {
        return query == null || query.isBlank() ? null : query.strip();
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
        Set<UUID> knowledgeSpaceIds = visibleSources.stream()
                .map(SourceObject::getKnowledgeSpaceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, SourceKnowledgeSpaceRef> spaceById = knowledgeSpaces.describeAll(
                organizationId, knowledgeSpaceIds);
        Set<UUID> departmentIds = visibleSources.stream()
                .map(SourceObject::getDepartmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> departmentNameById = provenance.departmentNames(
                organizationId, departmentIds);
        Set<UUID> uploaderIds = visibleSources.stream()
                .map(SourceObject::getCreatedByUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<UUID, String> uploaderNameById = provenance.userNames(
                organizationId, uploaderIds);
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
                    SourceKnowledgeSpaceRef space = Objects.requireNonNull(
                            spaceById.get(source.getKnowledgeSpaceId()),
                            "Source Knowledge Space was not found");
                    return summary(
                            source,
                            revision,
                            profile,
                            space,
                            name(departmentNameById, source.getDepartmentId()),
                            name(uploaderNameById, source.getCreatedByUserId()),
                            contentVisible.contains(source.getId()),
                            revision.getKnowledgeAssetId() != null
                                    && deletableAssetIds.contains(revision.getKnowledgeAssetId()));
                })
                .toList();
    }

    /** Organization-wide sources carry no department, and the name maps reject null keys. */
    private static String name(Map<UUID, String> namesById, UUID id) {
        return id == null ? null : namesById.get(id);
    }

    static SourceSummary summary(
            SourceObject source,
            SourceRevision revision,
            SourceEmbeddingProfileView embeddingProfile,
            SourceKnowledgeSpaceRef knowledgeSpace,
            String owningDepartmentName,
            String uploadedByName) {
        return summary(
                source,
                revision,
                embeddingProfile,
                knowledgeSpace,
                owningDepartmentName,
                uploadedByName,
                false,
                false);
    }

    static SourceSummary summary(
            SourceObject source,
            SourceRevision revision,
            SourceEmbeddingProfileView embeddingProfile,
            SourceKnowledgeSpaceRef knowledgeSpace,
            String owningDepartmentName,
            String uploadedByName,
            boolean contentAuthorized,
            boolean deleteAuthorized) {
        boolean ready = revision.getStatus() == SourceRevisionStatus.READY;
        UUID knowledgeAssetId = revision.getKnowledgeAssetId();
        boolean publicationComplete = ready && knowledgeAssetId != null;
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
                knowledgeSpace.key(),
                knowledgeSpace.name(),
                owningDepartmentName,
                uploadedByName,
                publicationComplete,
                publicationComplete && contentAuthorized,
                publicationComplete
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
