package com.orgmemory.api.admin;

import com.orgmemory.core.knowledge.SourceConnectionView;
import com.orgmemory.core.knowledge.SourceGroupView;
import com.orgmemory.core.knowledge.SourceIdentityTrust;
import com.orgmemory.core.knowledge.SourcePrincipalAdminService;
import com.orgmemory.core.knowledge.SourcePrincipalKind;
import com.orgmemory.core.knowledge.SourcePrincipalMappingMethod;
import com.orgmemory.core.knowledge.SourcePrincipalMappingStatus;
import com.orgmemory.core.knowledge.SourcePrincipalView;
import com.orgmemory.core.organization.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration of the external identity ledger: which principals a crawl observed,
 * which of them resolve to an internal user, what a source group contained when it was
 * sealed, and how far each connection's identities may be trusted.
 */
@RestController
@RequestMapping("/api/admin")
class AdminSourceAccessController {

    private final AdminAccessGuard guard;
    private final SourcePrincipalAdminService sourceAdmin;

    AdminSourceAccessController(AdminAccessGuard guard, SourcePrincipalAdminService sourceAdmin) {
        this.guard = guard;
        this.sourceAdmin = sourceAdmin;
    }

    record AdminSourceConnectionResponse(
            String sourceSystem,
            String sourceConnectionKey,
            SourceIdentityTrust identityTrust,
            UUID trustDecidedByUserId,
            Instant trustDecidedAt,
            int userCount,
            int mappedUserCount,
            int unmappedUserCount,
            int groupCount,
            Instant lastSeenAt) {

        static AdminSourceConnectionResponse from(SourceConnectionView view) {
            return new AdminSourceConnectionResponse(
                    view.sourceSystem(),
                    view.sourceConnectionKey(),
                    view.identityTrust(),
                    view.trustDecidedByUserId(),
                    view.trustDecidedAt(),
                    view.userCount(),
                    view.mappedUserCount(),
                    view.unmappedUserCount(),
                    view.groupCount(),
                    view.lastSeenAt());
        }
    }

    record AdminSourceMappingResponse(
            UUID id,
            UUID appUserId,
            String appUserName,
            String appUserEmail,
            SourcePrincipalMappingMethod method,
            SourcePrincipalMappingStatus status,
            String evidence,
            Instant verifiedAt) {
    }

    record AdminSourcePrincipalResponse(
            UUID id,
            String sourceSystem,
            String sourceConnectionKey,
            String externalKey,
            SourcePrincipalKind kind,
            String observedEmail,
            String observedDisplayName,
            boolean ssoVerified,
            Instant lastSeenAt,
            AdminSourceMappingResponse mapping) {

        static AdminSourcePrincipalResponse from(SourcePrincipalView view) {
            return new AdminSourcePrincipalResponse(
                    view.id(),
                    view.sourceSystem(),
                    view.sourceConnectionKey(),
                    view.externalKey(),
                    view.kind(),
                    view.observedEmail(),
                    view.observedDisplayName(),
                    view.ssoVerified(),
                    view.lastSeenAt(),
                    view.mapping() == null
                            ? null
                            : new AdminSourceMappingResponse(
                                    view.mapping().id(),
                                    view.mapping().appUserId(),
                                    view.mapping().appUserName(),
                                    view.mapping().appUserEmail(),
                                    view.mapping().method(),
                                    view.mapping().status(),
                                    view.mapping().evidence(),
                                    view.mapping().verifiedAt()));
        }
    }

    record AdminSourceGroupMemberResponse(
            UUID principalId,
            String externalKey,
            String observedDisplayName,
            String observedEmail,
            UUID appUserId,
            String appUserName) {
    }

    record AdminSourceGroupResponse(
            UUID principalId,
            String sourceSystem,
            String sourceConnectionKey,
            String externalKey,
            String observedDisplayName,
            UUID sourceAclSnapshotId,
            long aclGeneration,
            Instant sealedAt,
            List<AdminSourceGroupMemberResponse> members) {

        static AdminSourceGroupResponse from(SourceGroupView view) {
            return new AdminSourceGroupResponse(
                    view.principalId(),
                    view.sourceSystem(),
                    view.sourceConnectionKey(),
                    view.externalKey(),
                    view.observedDisplayName(),
                    view.sourceAclSnapshotId(),
                    view.aclGeneration(),
                    view.sealedAt(),
                    view.members().stream()
                            .map(member -> new AdminSourceGroupMemberResponse(
                                    member.principalId(),
                                    member.externalKey(),
                                    member.observedDisplayName(),
                                    member.observedEmail(),
                                    member.appUserId(),
                                    member.appUserName()))
                            .toList());
        }
    }

    record IdentityTrustRequest(
            String sourceSystem, String sourceConnectionKey, SourceIdentityTrust identityTrust) {
    }

    record ConfirmMappingRequest(UUID appUserId) {
    }

    @GetMapping("/source-connections")
    @Operation(operationId = "listAdminSourceConnections", summary = "List observed connections and their trust level")
    List<AdminSourceConnectionResponse> connections(Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        return sourceAdmin.listConnections(actor.organizationId()).stream()
                .map(AdminSourceConnectionResponse::from)
                .toList();
    }

    @PutMapping("/source-connections/identity-trust")
    @Operation(operationId = "setAdminSourceConnectionTrust", summary = "Record the identity trust for a connection")
    AdminSourceConnectionResponse setIdentityTrust(
            @RequestBody IdentityTrustRequest request, Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        return AdminSourceConnectionResponse.from(sourceAdmin.setIdentityTrust(
                actor.organizationId(),
                request.sourceSystem(),
                request.sourceConnectionKey(),
                request.identityTrust(),
                actor.userId()));
    }

    @GetMapping("/source-principals")
    @Operation(operationId = "listAdminSourcePrincipals", summary = "List observed principals and their mapping")
    List<AdminSourcePrincipalResponse> principals(Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        return sourceAdmin.listPrincipals(actor.organizationId()).stream()
                .map(AdminSourcePrincipalResponse::from)
                .toList();
    }

    @PutMapping("/source-principals/{principalId}/mapping")
    @Operation(operationId = "confirmAdminSourceMapping", summary = "Confirm a principal maps to an internal user")
    AdminSourcePrincipalResponse confirm(
            @PathVariable UUID principalId,
            @RequestBody ConfirmMappingRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        return AdminSourcePrincipalResponse.from(sourceAdmin.confirmMapping(
                actor.organizationId(), principalId, request.appUserId(), actor.userId()));
    }

    @DeleteMapping("/source-principals/{principalId}/mapping")
    @Operation(operationId = "revokeAdminSourceMapping", summary = "Revoke a principal's active mapping")
    AdminSourcePrincipalResponse revoke(@PathVariable UUID principalId, Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        return AdminSourcePrincipalResponse.from(
                sourceAdmin.revokeMapping(actor.organizationId(), principalId, actor.userId()));
    }

    @GetMapping("/source-groups")
    @Operation(operationId = "listAdminSourceGroups", summary = "List source groups with their sealed membership")
    List<AdminSourceGroupResponse> groups(Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        return sourceAdmin.listGroups(actor.organizationId()).stream()
                .map(AdminSourceGroupResponse::from)
                .toList();
    }
}
