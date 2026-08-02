package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.acl.AclAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceQueryServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            DEPARTMENT_ID,
            "Nguyen Van An",
            "an@example.com");

    private final SourceObjectRepository sources = mock(SourceObjectRepository.class);
    private final SourceRevisionRepository revisions = mock(SourceRevisionRepository.class);
    private final SourceEmbeddingProfileDirectory profiles = mock(SourceEmbeddingProfileDirectory.class);
    private final SourceVisibilityPort visibility = mock(SourceVisibilityPort.class);
    private final SourceActionAuthorizationPort actions = mock(SourceActionAuthorizationPort.class);
    private final SourceQueryService service = new SourceQueryService(
            sources,
            revisions,
            profiles,
            visibility,
            actions);

    @Test
    void listsOwnUploadsAndPermissionFilteredPublishedSources() {
        UUID ownSourceId = UUID.randomUUID();
        UUID sharedSourceId = UUID.randomUUID();
        UUID ownRevisionId = UUID.randomUUID();
        UUID sharedRevisionId = UUID.randomUUID();
        SourceObject ownSource = source(ownSourceId, ownRevisionId, "own.md");
        SourceObject sharedSource = source(sharedSourceId, sharedRevisionId, "shared.md");
        SourceRevision ownRevision = revision(ownRevisionId, "own.md");
        SourceRevision sharedRevision = revision(sharedRevisionId, "shared.md");
        UUID ownAssetId = UUID.randomUUID();
        UUID sharedAssetId = UUID.randomUUID();
        when(ownRevision.getKnowledgeAssetId()).thenReturn(ownAssetId);
        when(sharedRevision.getKnowledgeAssetId()).thenReturn(sharedAssetId);

        when(sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        ORGANIZATION_ID, USER_ID))
                .thenReturn(List.of(ownSource));
        when(visibility.visibleSourceObjectIds(ACTOR)).thenReturn(List.of(sharedSourceId));
        when(actions.deletableKnowledgeAssetIds(ACTOR)).thenReturn(Set.of(ownAssetId));
        when(sources.findAllByOrganizationIdAndIdInOrderByUpdatedAtDesc(
                        ORGANIZATION_ID, Set.of(ownSourceId, sharedSourceId)))
                .thenReturn(List.of(sharedSource, ownSource));
        when(revisions.findAllById(List.of(sharedRevisionId, ownRevisionId)))
                .thenReturn(List.of(sharedRevision, ownRevision));

        List<SourceSummary> result = service.listVisible(ACTOR);

        assertEquals(List.of(sharedSourceId, ownSourceId), result.stream().map(SourceSummary::id).toList());
        assertEquals(List.of(true, false), result.stream().map(SourceSummary::contentAvailable).toList());
        assertEquals(List.of(false, true), result.stream().map(SourceSummary::deletionAllowed).toList());
        verify(visibility).visibleSourceObjectIds(ACTOR);
        verify(actions).deletableKnowledgeAssetIds(ACTOR);
    }

    @Test
    void listOwnKeepsReceivedMetadataWithoutConsultingPublishedPermissions() {
        UUID sourceId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        SourceObject source = source(sourceId, revisionId, "pending.txt");
        SourceRevision revision = revision(revisionId, "pending.txt");
        when(revision.getStatus()).thenReturn(SourceRevisionStatus.RECEIVED);
        when(revisions.findAllById(List.of(revisionId))).thenReturn(List.of(revision));
        when(sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        ORGANIZATION_ID, USER_ID))
                .thenReturn(List.of(source));

        List<SourceSummary> result = service.listOwn(ACTOR);

        assertEquals(1, result.size());
        assertEquals(false, result.getFirst().contentAvailable());
        assertEquals(false, result.getFirst().deletionAllowed());
        verify(visibility, never()).visibleSourceObjectIds(ACTOR);
        verify(actions, never()).deletableKnowledgeAssetIds(ACTOR);
    }

    private static SourceObject source(UUID sourceId, UUID revisionId, String title) {
        SourceObject source = mock(SourceObject.class);
        when(source.getId()).thenReturn(sourceId);
        when(source.getCurrentRevisionId()).thenReturn(revisionId);
        when(source.getLatestRevisionId()).thenReturn(revisionId);
        when(source.getTitle()).thenReturn(title);
        when(source.getSourceSystem()).thenReturn("upload");
        when(source.getAclAuthority()).thenReturn(AclAuthority.ORGMEMORY);
        when(source.getClassification()).thenReturn(KnowledgeClassification.INTERNAL);
        when(source.getStatus()).thenReturn(SourceObjectStatus.ACTIVE);
        return source;
    }

    private static SourceRevision revision(UUID revisionId, String fileName) {
        SourceRevision revision = mock(SourceRevision.class);
        when(revision.getId()).thenReturn(revisionId);
        when(revision.getStatus()).thenReturn(SourceRevisionStatus.READY);
        when(revision.getFileName()).thenReturn(fileName);
        when(revision.getMediaType()).thenReturn("text/markdown");
        return revision;
    }
}
