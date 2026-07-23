package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.PermissionKey;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.organization.UserRole;
import java.time.Instant;
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

    private static final PermissionKey CAN_VIEW = PermissionKey.of("can_view");
    private static final String RESOURCE_TYPE = "knowledge_asset";

    private final SourceObjectRepository sources;
    private final SourceRevisionRepository revisions;
    private final EmbeddingProfileRegistry embeddingProfiles;
    private final AppUserRepository users;
    private final RelationshipAuthorizationSetPort authorization;
    private final SecureKnowledgeRetrievalStore visibility;
    private final KnowledgeRetrievalProperties retrievalProperties;

    SourceQueryService(
            SourceObjectRepository sources,
            SourceRevisionRepository revisions,
            EmbeddingProfileRegistry embeddingProfiles,
            AppUserRepository users,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore visibility,
            KnowledgeRetrievalProperties retrievalProperties) {
        this.sources = sources;
        this.revisions = revisions;
        this.embeddingProfiles = embeddingProfiles;
        this.users = users;
        this.authorization = authorization;
        this.visibility = visibility;
        this.retrievalProperties = retrievalProperties;
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
        AppUser subject = users.findById(actor.userId())
                .filter(user -> user.getOrganizationId().equals(actor.organizationId()) && user.isActive())
                .orElseThrow(() -> new OrgMemoryAccessDeniedException("Knowledge access profile is incomplete"));

        Set<UUID> sourceIds = new LinkedHashSet<>();
        sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        actor.organizationId(), actor.userId())
                .forEach(source -> sourceIds.add(source.getId()));

        var listed = authorization.listAuthorizedResources(new AuthorizedResourceQuery(
                actor.organizationId(), actor.principal(), CAN_VIEW, RESOURCE_TYPE));
        if (!listed.resolved()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Document permissions are temporarily unavailable");
        }
        List<ResourceRef> resources = listed.resources().stream()
                .filter(resource -> actor.organizationId().equals(resource.organizationId())
                        && RESOURCE_TYPE.equals(resource.type()))
                .distinct()
                .toList();
        if (resources.size() != listed.resources().size()
                || resources.size() > retrievalProperties.maximumAuthorizedObjects()) {
            throw new KnowledgeRetrievalUnavailableException(
                    "Document permissions are inconsistent");
        }
        if (!resources.isEmpty()) {
            List<UUID> assetIds;
            try {
                assetIds = resources.stream().map(resource -> UUID.fromString(resource.id())).toList();
            } catch (IllegalArgumentException invalidResource) {
                throw new KnowledgeRetrievalUnavailableException(
                        "Document permissions are inconsistent");
            }
            var scope = new SecureKnowledgeRetrievalStore.RetrievalScope(
                    actor.organizationId(),
                    actor.userId(),
                    subject.getDepartmentId(),
                    subject.getRole() == UserRole.EXECUTIVE,
                    assetIds,
                    listed.policyVersion(),
                    Instant.now());
            sourceIds.addAll(visibility.visibleSourceObjectIds(scope));
        }
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
        Map<UUID, EmbeddingProfileRef> profileById = new LinkedHashMap<>();
        return visibleSources.stream()
                .map(source -> {
                    SourceRevision revision = Objects.requireNonNull(
                            revisionById.get(source.getLatestRevisionId()),
                            "Source latest revision was not found");
                    EmbeddingProfileRef profile = revision.getEmbeddingProfileId() == null
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
            EmbeddingProfileRef embeddingProfile) {
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
