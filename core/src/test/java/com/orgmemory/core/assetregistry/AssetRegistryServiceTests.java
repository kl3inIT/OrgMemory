package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetRegistryServiceTests {

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
                        true)));
        RelationshipAuthorizationPort authorization =
                mock(RelationshipAuthorizationPort.class);
        when(authorization.check(any())).thenReturn(
                AuthorizationDecision.allow("model-v1"));
        AssetRegistryService service = new AssetRegistryService(
                coordinator,
                mock(AssetAuthorizationProjectionService.class),
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

        assertEquals("asset.role-assignment-invalid", missingType.code());
        assertEquals("asset.role-assignment-invalid", missingId.code());
    }
}
