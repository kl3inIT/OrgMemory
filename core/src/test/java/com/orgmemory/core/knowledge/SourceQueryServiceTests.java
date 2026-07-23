package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.UserRole;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.List;
import java.util.Optional;
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
    private final EmbeddingProfileRegistry profiles = mock(EmbeddingProfileRegistry.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final RelationshipAuthorizationSetPort authorization = mock(RelationshipAuthorizationSetPort.class);
    private final SecureKnowledgeRetrievalStore visibility = mock(SecureKnowledgeRetrievalStore.class);
    private final KnowledgeRetrievalProperties properties = new KnowledgeRetrievalProperties(null, null, null, null);
    private final SourceQueryService service = new SourceQueryService(
            sources,
            revisions,
            profiles,
            users,
            authorization,
            visibility,
            properties);

    @Test
    void listsOwnUploadsAndPermissionFilteredPublishedSources() {
        UUID ownSourceId = UUID.randomUUID();
        UUID sharedSourceId = UUID.randomUUID();
        UUID ownRevisionId = UUID.randomUUID();
        UUID sharedRevisionId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        SourceObject ownSource = source(ownSourceId, ownRevisionId, "own.md");
        SourceObject sharedSource = source(sharedSourceId, sharedRevisionId, "shared.md");
        SourceRevision ownRevision = revision(ownRevisionId, "own.md");
        SourceRevision sharedRevision = revision(sharedRevisionId, "shared.md");

        when(users.findById(USER_ID)).thenReturn(Optional.of(activeEmployee()));
        when(sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        ORGANIZATION_ID, USER_ID))
                .thenReturn(List.of(ownSource));
        when(authorization.listAuthorizedResources(any())).thenReturn(AuthorizedResourceSetResult.resolved(
                List.of(ResourceRef.of(ORGANIZATION_ID, "knowledge_asset", assetId)),
                "model-1"));
        when(visibility.visibleSourceObjectIds(any())).thenReturn(List.of(sharedSourceId));
        when(sources.findAllByOrganizationIdAndIdInOrderByUpdatedAtDesc(
                        ORGANIZATION_ID, Set.of(ownSourceId, sharedSourceId)))
                .thenReturn(List.of(sharedSource, ownSource));
        when(revisions.findAllById(List.of(sharedRevisionId, ownRevisionId)))
                .thenReturn(List.of(sharedRevision, ownRevision));

        List<SourceSummary> result = service.listVisible(ACTOR);

        assertEquals(List.of(sharedSourceId, ownSourceId), result.stream().map(SourceSummary::id).toList());
        verify(visibility).visibleSourceObjectIds(any());
    }

    @Test
    void authorizationOutageFailsClosedInsteadOfReturningAPartialList() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(activeEmployee()));
        when(sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        ORGANIZATION_ID, USER_ID))
                .thenReturn(List.of());
        when(authorization.listAuthorizedResources(any()))
                .thenReturn(AuthorizedResourceSetResult.indeterminate("OPENFGA_UNAVAILABLE", "model-1"));

        assertThrows(KnowledgeRetrievalUnavailableException.class, () -> service.listVisible(ACTOR));
    }

    @Test
    void platformAdminDoesNotBypassOpenFgaDataAuthorization() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(activeUser(UserRole.ADMIN)));
        when(sources.findAllByOrganizationIdAndCreatedByUserIdOrderByUpdatedAtDesc(
                        ORGANIZATION_ID, USER_ID))
                .thenReturn(List.of());
        when(authorization.listAuthorizedResources(any()))
                .thenReturn(AuthorizedResourceSetResult.indeterminate("OPENFGA_UNAVAILABLE", "model-1"));

        assertThrows(KnowledgeRetrievalUnavailableException.class, () -> service.listVisible(ACTOR));
        verify(authorization).listAuthorizedResources(any());
    }

    private static AppUser activeEmployee() {
        return activeUser(UserRole.EMPLOYEE);
    }

    private static AppUser activeUser(UserRole role) {
        return new AppUser(
                ORGANIZATION_ID,
                DEPARTMENT_ID,
                "Nguyen Van An",
                "an@example.com",
                role);
    }

    private static SourceObject source(UUID sourceId, UUID revisionId, String title) {
        SourceObject source = mock(SourceObject.class);
        when(source.getId()).thenReturn(sourceId);
        when(source.getCurrentRevisionId()).thenReturn(revisionId);
        when(source.getLatestRevisionId()).thenReturn(revisionId);
        when(source.getTitle()).thenReturn(title);
        when(source.getSourceType()).thenReturn(SourceType.UPLOAD);
        when(source.getClassification()).thenReturn(KnowledgeClassification.INTERNAL);
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
