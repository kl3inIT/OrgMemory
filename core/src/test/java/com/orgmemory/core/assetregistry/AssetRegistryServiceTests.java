package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetAuthorizationProjectionCommand;
import com.orgmemory.core.assetregistry.api.AssetAuthorizationTarget;
import com.orgmemory.core.assetregistry.api.AssetRole;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.AuthorizedResourceQuery;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationQuery;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.ResourceRef;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssetRegistryServiceTests {

    @Test
    void authorizationTargetRequiresCanonicalIdentityFields() {
        UUID organizationId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        UUID knowledgeSpaceId = UUID.randomUUID();

        assertThrows(
                NullPointerException.class,
                () -> new AssetAuthorizationTarget(
                        null, assetId, knowledgeSpaceId, AssetType.SKILL, true));
        assertThrows(
                NullPointerException.class,
                () -> new AssetAuthorizationTarget(
                        organizationId, null, knowledgeSpaceId, AssetType.SKILL, true));
        assertThrows(
                NullPointerException.class,
                () -> new AssetAuthorizationTarget(
                        organizationId, assetId, null, AssetType.SKILL, true));
        assertThrows(
                NullPointerException.class,
                () -> new AssetAuthorizationTarget(
                        organizationId, assetId, knowledgeSpaceId, null, true));
    }

    @Test
    void ownedWorkspaceResolvesVisibilityAndCanonicalOwnerAssignments() {
        UUID organizationId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                organizationId,
                null,
                "Owner",
                "owner@example.test");
        AssetRegistryCoordinator coordinator = mock(AssetRegistryCoordinator.class);
        RelationshipAuthorizationSetPort authorizationSets =
                mock(RelationshipAuthorizationSetPort.class);
        when(authorizationSets.listAuthorizedResources(any())).thenReturn(
                AuthorizedResourceSetResult.resolved(
                        List.of(ResourceRef.of(organizationId, "asset", assetId)),
                        "model-v1"));
        AssetSummaryPage expected = AssetSummaryPage.empty(
                1, 24, AssetOwnedSort.RECENTLY_UPDATED);
        when(coordinator.ownedSummaryPage(
                        eq(organizationId),
                        eq(actor.userId()),
                        eq(Set.of(assetId)),
                        eq(null),
                        eq(null),
                        eq(AssetOwnedSort.RECENTLY_UPDATED),
                        eq(1),
                        eq(24)))
                .thenReturn(expected);
        AssetRegistryService service = new AssetRegistryService(
                coordinator,
                mock(AssetAuthorizationProjectionCommand.class),
                mock(RelationshipAuthorizationPort.class),
                authorizationSets);

        assertEquals(expected, service.owned(actor, null, null, null, 1, 24));

        ArgumentCaptor<AuthorizedResourceQuery> query =
                ArgumentCaptor.forClass(AuthorizedResourceQuery.class);
        verify(authorizationSets).listAuthorizedResources(query.capture());
        assertEquals("can_view", query.getValue().permission().value());
        assertEquals(actor.principal(), query.getValue().principal());
    }

    @Test
    void governanceActionsUseLivePermissionsAfterRequiringAssetView() {
        UUID organizationId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                organizationId,
                null,
                "Author",
                "author@example.test");
        AssetRegistryCoordinator coordinator =
                mock(AssetRegistryCoordinator.class);
        when(coordinator.target(organizationId, assetId)).thenReturn(
                Optional.of(new AssetAuthorizationTarget(
                        organizationId,
                        assetId,
                        UUID.randomUUID(),
                        AssetType.SKILL,
                        true)));
        when(coordinator.reviewDecisionActions(actor, assetId)).thenReturn(
                new AssetReviewDecisionActions(
                        false,
                        true,
                        true,
                        true));
        RelationshipAuthorizationPort authorization =
                mock(RelationshipAuthorizationPort.class);
        when(authorization.check(any())).thenAnswer(invocation -> {
            RelationshipAuthorizationQuery query = invocation.getArgument(0);
            return switch (query.permission().value()) {
                case "can_view_released", "can_edit", "can_submit_review", "can_review",
                        "can_publish", "can_publish_skill" ->
                    AuthorizationDecision.allow("model-v1");
                default -> AuthorizationDecision.deny(
                        "RELATIONSHIP_DENIED", "model-v1");
            };
        });
        AssetRegistryService service = new AssetRegistryService(
                coordinator,
                mock(AssetAuthorizationProjectionCommand.class),
                authorization,
                mock(RelationshipAuthorizationSetPort.class));

        AssetGovernanceActions actions =
                service.governanceActions(actor, assetId);

        assertEquals(true, actions.canEdit());
        assertEquals(true, actions.canSubmitReview());
        assertEquals(true, actions.canReview());
        assertEquals(false, actions.canApprove());
        assertEquals(true, actions.canRequestChanges());
        assertEquals(true, actions.canReject());
        assertEquals(true, actions.canCancel());
        assertEquals(true, actions.canPublish());
        assertEquals(true, actions.canPublishSkill());
        assertEquals(false, actions.canWithdraw());
        assertEquals(true, actions.canOpenGovernance());
    }

    @Test
    void rejectsMissingRolePrincipalFieldsAsBusinessValidation() {
        UUID organizationId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                organizationId,
                null,
                "Owner",
                "owner@example.test");
        AssetRegistryCoordinator coordinator =
                mock(AssetRegistryCoordinator.class);
        when(coordinator.target(organizationId, assetId)).thenReturn(
                Optional.of(new AssetAuthorizationTarget(
                        organizationId,
                        assetId,
                        UUID.randomUUID(),
                        AssetType.PROMPT_TEMPLATE,
                        true)));
        RelationshipAuthorizationPort authorization =
                mock(RelationshipAuthorizationPort.class);
        when(authorization.check(any())).thenReturn(
                AuthorizationDecision.allow("model-v1"));
        AssetRegistryService service = new AssetRegistryService(
                coordinator,
                mock(AssetAuthorizationProjectionCommand.class),
                authorization,
                mock(RelationshipAuthorizationSetPort.class));

        BusinessValidationException missingType = assertThrows(
                BusinessValidationException.class,
                () -> service.assignRole(
                        actor,
                        assetId,
                        null,
                        actor.userId().toString(),
                        AssetRole.VIEWER));
        BusinessValidationException missingId = assertThrows(
                BusinessValidationException.class,
                () -> service.assignRole(
                        actor,
                        assetId,
                        "user",
                        null,
                        AssetRole.VIEWER));

        assertEquals("asset.share-invalid", missingType.code());
        assertEquals("asset.share-invalid", missingId.code());
    }
}
