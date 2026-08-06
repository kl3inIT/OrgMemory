package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.acl.AclAuthority;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.OrganizationProvenanceQuery;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceQueryServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            DEPARTMENT_ID,
            "Nguyen Van An",
            "an@example.com");

    private final SourceObjectRepository sources = mock(SourceObjectRepository.class);
    private final SourceRevisionRepository revisions = mock(SourceRevisionRepository.class);
    private final SourceEmbeddingProfileDirectory profiles = mock(SourceEmbeddingProfileDirectory.class);
    private final SourceKnowledgeSpacePort spaces = mock(SourceKnowledgeSpacePort.class);
    private final OrganizationProvenanceQuery provenance = mock(OrganizationProvenanceQuery.class);
    private final SourceVisibilityPort visibility = mock(SourceVisibilityPort.class);
    private final SourceActionAuthorizationPort actions = mock(SourceActionAuthorizationPort.class);
    private final SourceQueryService service = new SourceQueryService(
            sources,
            revisions,
            profiles,
            spaces,
            provenance,
            visibility,
            actions);

    @Test
    void listsOwnUploadsAndPermissionFilteredPublishedSources() {
        UUID unauthorizedSourceId = UUID.randomUUID();
        UUID availableSourceId = UUID.randomUUID();
        UUID pendingSourceId = UUID.randomUUID();
        UUID unauthorizedRevisionId = UUID.randomUUID();
        UUID availableRevisionId = UUID.randomUUID();
        UUID pendingRevisionId = UUID.randomUUID();
        SourceObject unauthorizedSource = source(
                unauthorizedSourceId, unauthorizedRevisionId, "finance-plan.md");
        SourceObject availableSource = source(
                availableSourceId, availableRevisionId, "onboarding.md");
        SourceObject pendingSource = source(
                pendingSourceId, pendingRevisionId, "draft.md");
        SourceRevision unauthorizedRevision = revision(
                unauthorizedRevisionId, "finance-plan.md");
        SourceRevision availableRevision = revision(
                availableRevisionId, "onboarding.md");
        SourceRevision pendingRevision = revision(pendingRevisionId, "draft.md");
        UUID unauthorizedAssetId = UUID.randomUUID();
        UUID availableAssetId = UUID.randomUUID();
        when(unauthorizedRevision.getKnowledgeAssetId()).thenReturn(unauthorizedAssetId);
        when(availableRevision.getKnowledgeAssetId()).thenReturn(availableAssetId);
        when(pendingRevision.getStatus()).thenReturn(SourceRevisionStatus.PUBLISHING);

        when(sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        ORGANIZATION_ID, USER_ID))
                .thenReturn(List.of(unauthorizedSource, pendingSource));
        when(visibility.visibleSourceObjectIds(ACTOR)).thenReturn(List.of(availableSourceId));
        when(actions.deletableKnowledgeAssetIds(ACTOR)).thenReturn(Set.of(unauthorizedAssetId));
        when(sources.findAllByOrganizationIdAndIdInOrderByUpdatedAtDesc(
                        ORGANIZATION_ID,
                        Set.of(unauthorizedSourceId, pendingSourceId, availableSourceId)))
                .thenReturn(List.of(availableSource, unauthorizedSource, pendingSource));
        when(revisions.findAllById(List.of(
                        availableRevisionId, unauthorizedRevisionId, pendingRevisionId)))
                .thenReturn(List.of(availableRevision, unauthorizedRevision, pendingRevision));
        stubProvenance();

        List<SourceSummary> result = service.listVisible(ACTOR);

        assertEquals(
                List.of(availableSourceId, unauthorizedSourceId, pendingSourceId),
                result.stream().map(SourceSummary::id).toList());
        assertEquals(
                List.of(true, true, false),
                result.stream().map(SourceSummary::publicationComplete).toList());
        assertEquals(
                List.of(true, false, false),
                result.stream().map(SourceSummary::contentAvailable).toList());
        assertEquals(
                List.of(false, true, false),
                result.stream().map(SourceSummary::deletionAllowed).toList());
        assertEquals(
                List.of("people", "people", "people"),
                result.stream().map(SourceSummary::knowledgeSpaceKey).toList());
        assertEquals(
                List.of("People", "People", "People"),
                result.stream().map(SourceSummary::knowledgeSpaceName).toList());
        assertEquals(
                List.of("People Operations", "People Operations", "People Operations"),
                result.stream().map(SourceSummary::owningDepartmentName).toList());
        assertEquals(
                List.of("Nguyen Van An", "Nguyen Van An", "Nguyen Van An"),
                result.stream().map(SourceSummary::uploadedByName).toList());
        verify(visibility).visibleSourceObjectIds(ACTOR);
        verify(actions).deletableKnowledgeAssetIds(ACTOR);
        verify(spaces).describeAll(ORGANIZATION_ID, Set.of(SPACE_ID));
        verify(provenance).departmentNames(ORGANIZATION_ID, Set.of(DEPARTMENT_ID));
        verify(provenance).userNames(ORGANIZATION_ID, Set.of(USER_ID));
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
        stubProvenance();

        List<SourceSummary> result = service.listOwn(ACTOR);

        assertEquals(1, result.size());
        assertEquals(false, result.getFirst().publicationComplete());
        assertEquals(false, result.getFirst().contentAvailable());
        assertEquals(false, result.getFirst().deletionAllowed());
        verify(visibility, never()).visibleSourceObjectIds(ACTOR);
        verify(actions, never()).deletableKnowledgeAssetIds(ACTOR);
    }

    @Test
    void summarizesOrganizationWideSourcesThatCarryNoDepartment() {
        UUID sourceId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        SourceObject source = source(sourceId, revisionId, "employee-handbook.md");
        when(source.getDepartmentId()).thenReturn(null);
        when(source.getCreatedByUserId()).thenReturn(null);
        SourceRevision revision = revision(revisionId, "employee-handbook.md");
        when(revision.getKnowledgeAssetId()).thenReturn(UUID.randomUUID());
        when(revisions.findAllById(List.of(revisionId))).thenReturn(List.of(revision));
        when(sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        ORGANIZATION_ID, USER_ID))
                .thenReturn(List.of(source));
        when(spaces.describeAll(ORGANIZATION_ID, Set.of(SPACE_ID)))
                .thenReturn(Map.of(
                        SPACE_ID,
                        new SourceKnowledgeSpaceRef(SPACE_ID, "company", "Company", null)));
        when(provenance.departmentNames(ORGANIZATION_ID, Set.of())).thenReturn(Map.of());
        when(provenance.userNames(ORGANIZATION_ID, Set.of())).thenReturn(Map.of());

        List<SourceSummary> result = service.listOwn(ACTOR);

        assertEquals(1, result.size());
        assertEquals("company", result.getFirst().knowledgeSpaceKey());
        assertNull(result.getFirst().owningDepartmentName());
        assertNull(result.getFirst().uploadedByName());
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
        when(source.getKnowledgeSpaceId()).thenReturn(SPACE_ID);
        when(source.getDepartmentId()).thenReturn(DEPARTMENT_ID);
        when(source.getCreatedByUserId()).thenReturn(USER_ID);
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

    private void stubProvenance() {
        when(spaces.describeAll(ORGANIZATION_ID, Set.of(SPACE_ID)))
                .thenReturn(Map.of(
                        SPACE_ID,
                        new SourceKnowledgeSpaceRef(
                                SPACE_ID, "people", "People", DEPARTMENT_ID)));
        when(provenance.departmentNames(ORGANIZATION_ID, Set.of(DEPARTMENT_ID)))
                .thenReturn(Map.of(DEPARTMENT_ID, "People Operations"));
        when(provenance.userNames(ORGANIZATION_ID, Set.of(USER_ID)))
                .thenReturn(Map.of(USER_ID, "Nguyen Van An"));
    }
}
