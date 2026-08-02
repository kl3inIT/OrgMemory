package com.orgmemory.core.assetregistry.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.assetregistry.api.AssetPortfolioState;
import com.orgmemory.core.assetregistry.api.AssetRegistrationCommand;
import com.orgmemory.core.assetregistry.api.AssetRole;
import com.orgmemory.core.assetregistry.api.AssetRoleCommand;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.authorization.PrincipalRef;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

class AssetKernelServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID SPACE_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final PrincipalRef OWNER = PrincipalRef.user(USER_ID);

    @Test
    void registrationWritesIdentityOwnerAndThreeAuthorizationIntentsTogether() {
        Fixture fixture = fixture();
        when(fixture.assets.saveAndFlush(any(Asset.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(fixture.roles.saveAndFlush(any(AssetRoleAssignment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID assetId = fixture.service.register(new AssetRegistrationCommand.NewAsset(
                ORGANIZATION_ID,
                AssetType.PROMPT_TEMPLATE,
                "Support",
                "triage",
                SPACE_ID,
                OWNER,
                USER_ID));

        verify(fixture.assets).saveAndFlush(any(Asset.class));
        verify(fixture.roles).saveAndFlush(any(AssetRoleAssignment.class));
        verify(fixture.outbox).saveAllAndFlush(org.mockito.ArgumentMatchers.argThat(records -> {
            List<AssetAuthorizationOutbox> intents = java.util.stream.StreamSupport
                    .stream(records.spliterator(), false)
                    .toList();
            return intents.size() == 3
                    && intents.stream().allMatch(intent -> intent.getAssetId().equals(assetId))
                    && intents.stream().map(AssetAuthorizationOutbox::tuple).anyMatch(tuple ->
                            tuple.user().equals("organization:" + ORGANIZATION_ID)
                                    && tuple.relation().equals("organization"))
                    && intents.stream().map(AssetAuthorizationOutbox::tuple).anyMatch(tuple ->
                            tuple.user().equals("knowledge_space:" + SPACE_ID)
                                    && tuple.relation().equals("space"))
                    && intents.stream().map(AssetAuthorizationOutbox::tuple).anyMatch(tuple ->
                            tuple.user().equals(OWNER.openFgaUser())
                                    && tuple.relation().equals("owner"));
        }));
    }

    @Test
    void duplicateActiveRoleDoesNotAppendAnotherAuthorizationIntent() {
        Fixture fixture = fixture();
        Asset asset = asset();
        when(fixture.assets.findForUpdate(asset.getId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(asset));
        when(fixture.roles
                        .findByAssetIdAndPrincipalTypeAndPrincipalIdAndRoleAndValidUntilIsNull(
                                asset.getId(), "user", USER_ID.toString(), AssetRole.OWNER))
                .thenReturn(Optional.of(mock(AssetRoleAssignment.class)));

        assertThrows(
                AssetConflictException.class,
                () -> fixture.service.assign(new AssetRoleCommand.Assignment(
                        ORGANIZATION_ID,
                        asset.getId(),
                        OWNER,
                        AssetRole.OWNER,
                        USER_ID)));

        verify(fixture.outbox, never()).saveAndFlush(any());
    }

    @Test
    void portfolioTransitionsRemainOwnedByTheLockedCanonicalAsset() {
        Fixture fixture = fixture();
        Asset asset = asset();
        when(fixture.assets.findForUpdate(asset.getId(), ORGANIZATION_ID))
                .thenReturn(Optional.of(asset));
        when(fixture.assets.save(asset)).thenReturn(asset);

        assertEquals(
                AssetPortfolioState.ACTIVE,
                fixture.service.activateAfterRelease(ORGANIZATION_ID, asset.getId())
                        .portfolioState());
        assertEquals(
                AssetPortfolioState.SUNSETTING,
                fixture.service.startSunsettingAfterReleaseChange(
                                ORGANIZATION_ID, asset.getId())
                        .portfolioState());
        assertEquals(
                AssetPortfolioState.RETIRED,
                fixture.service.retireAfterFinalWithdrawal(ORGANIZATION_ID, asset.getId())
                        .portfolioState());
    }

    @Test
    void kernelCommandsJoinTheParentTransactionAndQueueOperationsOwnShortTransactions()
            throws NoSuchMethodException {
        assertPropagation(
                AssetKernelService.class.getMethod(
                        "register", AssetRegistrationCommand.NewAsset.class),
                Propagation.MANDATORY);
        assertPropagation(
                AssetKernelService.class.getMethod(
                        "assign", AssetRoleCommand.Assignment.class),
                Propagation.MANDATORY);
        assertPropagation(
                AssetKernelService.class.getMethod(
                        "activateAfterRelease", UUID.class, UUID.class),
                Propagation.MANDATORY);
        assertPropagation(
                AssetAuthorizationCoordinator.class.getMethod(
                        "claimForAsset", UUID.class, UUID.class),
                Propagation.REQUIRES_NEW);
        assertPropagation(
                AssetAuthorizationCoordinator.class.getMethod("claimPending", int.class),
                Propagation.REQUIRES_NEW);
    }

    private static void assertPropagation(Method method, Propagation expected) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertTrue(transactional != null, () -> method + " must declare @Transactional");
        assertEquals(expected, transactional.propagation());
    }

    private static Asset asset() {
        return new Asset(
                ORGANIZATION_ID,
                AssetType.PROMPT_TEMPLATE,
                "support",
                "triage",
                SPACE_ID);
    }

    private static Fixture fixture() {
        AssetRepository assets = mock(AssetRepository.class);
        AssetRoleAssignmentRepository roles = mock(AssetRoleAssignmentRepository.class);
        AssetAuthorizationOutboxRepository outbox =
                mock(AssetAuthorizationOutboxRepository.class);
        return new Fixture(new AssetKernelService(assets, roles, outbox), assets, roles, outbox);
    }

    private record Fixture(
            AssetKernelService service,
            AssetRepository assets,
            AssetRoleAssignmentRepository roles,
            AssetAuthorizationOutboxRepository outbox) {
    }
}
