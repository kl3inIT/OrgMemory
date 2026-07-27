package com.orgmemory.api.admin;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.api.scim.ScimCredentialAdministrationService;
import com.orgmemory.core.identityprovisioning.ProvisioningLedgerService;
import com.orgmemory.core.identityprovisioning.ProvisioningOperationalState;
import com.orgmemory.core.identityprovisioning.ProvisioningProviderProfile;
import com.orgmemory.core.organization.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/provisioning")
class AdminProvisioningController {

    private final AdminAccessGuard guard;
    private final ProvisioningLedgerService ledger;
    private final ScimCredentialAdministrationService credentialAdministration;

    AdminProvisioningController(
            AdminAccessGuard guard,
            ProvisioningLedgerService ledger,
            ScimCredentialAdministrationService credentialAdministration) {
        this.guard = guard;
        this.ledger = ledger;
        this.credentialAdministration = credentialAdministration;
    }

    record CreateConnectionRequest(
            String alias,
            ProvisioningProviderProfile providerProfile) {
    }

    record ConnectionResponse(
            UUID id,
            String alias,
            ProvisioningProviderProfile providerProfile,
            String configurationStatus,
            ProvisioningOperationalState operationalState,
            boolean usersEnabled,
            boolean groupsEnabled,
            long version) {

        static ConnectionResponse from(ProvisioningLedgerService.ConnectionView view) {
            return new ConnectionResponse(
                    view.id(),
                    view.alias(),
                    view.providerProfile(),
                    view.configurationStatus().name(),
                    view.operationalState(),
                    view.usersEnabled(),
                    view.groupsEnabled(),
                    view.version());
        }
    }

    record IssueCredentialRequest(boolean usersScope, boolean groupsScope) {
    }

    record IssuedCredentialResponse(
            UUID credentialId,
            String token,
            String publicTokenId,
            boolean usersScope,
            boolean groupsScope,
            Instant expiresAt) {

        static IssuedCredentialResponse from(
                ScimCredentialAdministrationService.IssuedCredential issued) {
            return new IssuedCredentialResponse(
                    issued.credentialId(),
                    issued.token(),
                    issued.publicTokenId(),
                    issued.usersScope(),
                    issued.groupsScope(),
                    issued.expiresAt());
        }
    }

    record CredentialResponse(
            UUID id,
            String publicTokenId,
            int verifierKeyVersion,
            boolean usersScope,
            boolean groupsScope,
            Instant expiresAt,
            Instant overlapEndsAt,
            Instant revokedAt,
            Instant lastUsedAt,
            Instant createdAt) {

        static CredentialResponse from(ProvisioningLedgerService.CredentialView view) {
            return new CredentialResponse(
                    view.id(),
                    view.publicTokenId(),
                    view.verifierKeyVersion(),
                    view.usersScope(),
                    view.groupsScope(),
                    view.expiresAt(),
                    view.overlapEndsAt(),
                    view.revokedAt(),
                    view.lastUsedAt(),
                    view.createdAt());
        }
    }

    record UpdateStateRequest(
            long expectedVersion,
            ProvisioningOperationalState expectedState,
            ProvisioningOperationalState nextState) {
    }

    @GetMapping("/connections")
    @Operation(operationId = "listProvisioningConnections", summary = "List SCIM provisioning connections")
    List<ConnectionResponse> connections(Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        return ledger.listConnections(actor.organizationId()).stream()
                .map(ConnectionResponse::from)
                .toList();
    }

    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createProvisioningConnection", summary = "Create a disabled SCIM connection")
    ConnectionResponse create(
            @RequestBody CreateConnectionRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        if (request.alias() == null || request.alias().isBlank()) {
            throw new ApiRequestException("A provisioning connection name is required");
        }
        ProvisioningProviderProfile profile = request.providerProfile() == null
                ? ProvisioningProviderProfile.GENERIC_SCIM
                : request.providerProfile();
        return ConnectionResponse.from(ledger.createDisabledConnection(
                actor.organizationId(), request.alias(), profile, true, false));
    }

    @PatchMapping("/connections/{connectionId}/state")
    @Operation(operationId = "updateProvisioningConnectionState", summary = "Compare-and-set SCIM connection state")
    ConnectionResponse updateState(
            @PathVariable UUID connectionId,
            @RequestBody UpdateStateRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        if (request.expectedState() == null || request.nextState() == null) {
            throw new ApiRequestException("Expected and next provisioning states are required");
        }
        if (request.nextState() == ProvisioningOperationalState.VALIDATING
                || request.nextState() == ProvisioningOperationalState.ENABLED) {
            throw new ApiRequestException(
                    "SCIM provisioning enablement is not available in this increment");
        }
        if (!ledger.compareAndSetOperationalState(
                actor.organizationId(),
                connectionId,
                request.expectedVersion(),
                request.expectedState(),
                request.nextState())) {
            throw new ApiRequestException("The provisioning connection state changed; refresh and retry");
        }
        return ConnectionResponse.from(
                ledger.requireConnection(actor.organizationId(), connectionId));
    }

    @GetMapping("/connections/{connectionId}/credentials")
    @Operation(operationId = "listProvisioningCredentials", summary = "List SCIM credential metadata")
    List<CredentialResponse> credentials(
            @PathVariable UUID connectionId,
            Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        return ledger.listCredentials(actor.organizationId(), connectionId).stream()
                .map(CredentialResponse::from)
                .toList();
    }

    @PostMapping("/connections/{connectionId}/credentials")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "issueProvisioningCredential", summary = "Issue a one-time SCIM credential")
    IssuedCredentialResponse issue(
            @PathVariable UUID connectionId,
            @RequestBody IssueCredentialRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        requireFoundationScope(request);
        return IssuedCredentialResponse.from(credentialAdministration.issue(
                actor.organizationId(),
                connectionId,
                actor.userId(),
                request.usersScope(),
                request.groupsScope()));
    }

    @PostMapping("/connections/{connectionId}/credentials/{credentialId}/rotate")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "rotateProvisioningCredential", summary = "Rotate a SCIM credential with bounded overlap")
    IssuedCredentialResponse rotate(
            @PathVariable UUID connectionId,
            @PathVariable UUID credentialId,
            @RequestBody IssueCredentialRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        requireFoundationScope(request);
        return IssuedCredentialResponse.from(credentialAdministration.rotate(
                actor.organizationId(),
                connectionId,
                credentialId,
                actor.userId(),
                request.usersScope(),
                request.groupsScope()));
    }

    @DeleteMapping("/connections/{connectionId}/credentials/{credentialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "revokeProvisioningCredential", summary = "Immediately revoke a SCIM credential")
    void revoke(
            @PathVariable UUID connectionId,
            @PathVariable UUID credentialId,
            Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        ledger.revokeCredential(
                actor.organizationId(), connectionId, credentialId, actor.userId());
    }

    private static void requireFoundationScope(IssueCredentialRequest request) {
        if (request == null || !request.usersScope() || request.groupsScope()) {
            throw new ApiRequestException(
                    "This increment supports a Users-only SCIM credential");
        }
    }
}
