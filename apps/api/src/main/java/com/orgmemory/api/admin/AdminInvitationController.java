package com.orgmemory.api.admin;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.UserInvitation;
import com.orgmemory.core.organization.UserProvisioningService;
import com.orgmemory.core.organization.Clearance;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Addresses expected to sign in, and what became of them.
 *
 * <p>This is a separate list from users on purpose. An invitation has no user row behind it — it
 * is a statement about somebody who has not arrived — and folding the two together is what made
 * "not linked" mean two different things at once: never expected, or expected and not yet here.
 */
@RestController
@RequestMapping("/api/admin/invitations")
class AdminInvitationController {

    private final AdminAccessGuard guard;
    private final UserProvisioningService provisioning;

    AdminInvitationController(AdminAccessGuard guard, UserProvisioningService provisioning) {
        this.guard = guard;
        this.provisioning = provisioning;
    }

    /** {@code status} is OPEN, ACCEPTED, or REVOKED — the whole life of the expectation. */
    record AdminInvitationResponse(
            UUID id,
            String email,
            Clearance clearance,
            UUID departmentId,
            String status,
            Instant invitedAt,
            Instant acceptedAt,
            UUID acceptedAppUserId) {
    }

    record CreateInvitationRequest(String email, Clearance clearance, UUID departmentId) {
    }

    @GetMapping
    @Operation(operationId = "listAdminInvitations", summary = "List invited addresses and their status")
    @Transactional(readOnly = true)
    List<AdminInvitationResponse> list(Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        return provisioning.forOrganization(actor.organizationId()).stream()
                .map(AdminInvitationController::response)
                .toList();
    }

    @PostMapping
    @Operation(operationId = "createAdminInvitation", summary = "Expect an address to sign in")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    AdminInvitationResponse create(
            @RequestBody CreateInvitationRequest request, Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        if (request.email() == null || request.email().isBlank()) {
            throw new ApiRequestException("An email is required");
        }
        if (request.clearance() == null) {
            throw new ApiRequestException("A clearance is required");
        }
        return response(provisioning.invite(
                actor.organizationId(),
                request.email(),
                request.departmentId(),
                request.clearance(),
                actor.userId()));
    }

    @DeleteMapping("/{invitationId}")
    @Operation(operationId = "revokeAdminInvitation", summary = "Withdraw an invitation that has not been used")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void revoke(@PathVariable UUID invitationId, Authentication authentication) {
        CurrentActor actor = guard.requireMemberAdministrator(authentication);
        provisioning.revoke(actor.organizationId(), invitationId);
    }

    private static AdminInvitationResponse response(UserInvitation invitation) {
        return new AdminInvitationResponse(
                invitation.getId(),
                invitation.getEmail(),
                invitation.getClearance(),
                invitation.getDepartmentId(),
                invitation.getRevokedAt() != null
                        ? "REVOKED"
                        : invitation.getAcceptedAt() != null ? "ACCEPTED" : "OPEN",
                invitation.getCreatedAt(),
                invitation.getAcceptedAt(),
                invitation.getAcceptedAppUserId());
    }
}
