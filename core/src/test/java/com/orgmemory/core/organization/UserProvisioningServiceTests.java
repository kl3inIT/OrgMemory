package com.orgmemory.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.shared.error.BusinessConflictException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UserProvisioningServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_ORGANIZATION_ID = UUID.fromString("99999999-9999-4999-8999-999999999999");
    private static final UUID DEPARTMENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID INVITER_ID = UUID.fromString("13000000-0000-4000-8000-000000000001");
    private static final String ISSUER = "http://localhost:8180/realms/orgmemory";
    private static final String SUBJECT = "77777777-7777-4777-8777-777777777777";

    private AppUserRepository users;
    private OrganizationRepository organizations;
    private DepartmentRepository departments;
    private ExternalIdentityBindingService identityBindings;
    private UserInvitationRepository invitations;
    private UserProvisioningService service;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        organizations = mock(OrganizationRepository.class);
        departments = mock(DepartmentRepository.class);
        identityBindings = mock(ExternalIdentityBindingService.class);
        invitations = mock(UserInvitationRepository.class);
        when(organizations.existsById(ORGANIZATION_ID)).thenReturn(true);
        when(departments.existsByIdAndOrganizationId(DEPARTMENT_ID, ORGANIZATION_ID))
                .thenReturn(true);
        when(users.existsByIdAndOrganizationId(INVITER_ID, ORGANIZATION_ID)).thenReturn(true);
        when(identityBindings.findUserId(anyString(), anyString())).thenReturn(Optional.empty());
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(invitations.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new UserProvisioningService(
                users, organizations, departments, identityBindings, invitations);
    }

    private UserInvitation invitation(UUID organizationId, String email) {
        return new UserInvitation(organizationId, email, DEPARTMENT_ID, UserRole.EMPLOYEE, INVITER_ID);
    }

    @Test
    void anInvitedAddressBecomesAUserBoundToItsSubject() {
        when(invitations.findOpenByEmailForUpdate("newcomer@example.com"))
                .thenReturn(List.of(invitation(ORGANIZATION_ID, "newcomer@example.com")));
        when(users.findByOrganizationIdAndEmailIgnoreCase(
                        ORGANIZATION_ID, "newcomer@example.com"))
                .thenReturn(Optional.empty());

        var provisioned = service.provisionFromInvitation(ISSUER, SUBJECT, "Newcomer@Example.com");

        assertTrue(provisioned.isPresent());
        assertEquals("newcomer@example.com", provisioned.get().getEmail());
        assertEquals(ORGANIZATION_ID, provisioned.get().getOrganizationId());
        assertEquals(UserRole.EMPLOYEE, provisioned.get().getRole());
        verify(users).findByOrganizationIdAndEmailIgnoreCase(
                ORGANIZATION_ID, "newcomer@example.com");
        verify(identityBindings).bind(eq(provisioned.get().getId()), eq(ISSUER), eq(SUBJECT));
    }

    @Test
    void anAddressWithNoInvitationProvisionsNothing() {
        when(invitations.findOpenByEmailForUpdate(anyString())).thenReturn(List.of());

        assertTrue(service.provisionFromInvitation(ISSUER, SUBJECT, "stranger@example.com").isEmpty());
        verify(users, never()).save(any());
        verify(identityBindings, never()).bind(any(), anyString(), anyString());
    }

    @Test
    void aVerifiedDirectoryUserIsBoundWithoutAnInvitation() {
        var directoryUser = new AppUser(
                ORGANIZATION_ID,
                null,
                "Directory User",
                "directory@example.com",
                UserRole.EMPLOYEE);
        directoryUser.applyDirectoryAccess(true);
        when(users.findByEmailIgnoreCase("directory@example.com"))
                .thenReturn(List.of(directoryUser));

        var provisioned = service.provisionForVerifiedSignIn(
                ISSUER, SUBJECT, "Directory@Example.com");

        assertEquals(directoryUser.getId(), provisioned.orElseThrow().getId());
        verify(identityBindings).bind(directoryUser.getId(), ISSUER, SUBJECT);
        verify(invitations, never()).findOpenByEmailForUpdate(anyString());
    }

    @Test
    void anInvitationCannotAdoptADirectoryManagedUser() {
        var directoryUser = new AppUser(
                ORGANIZATION_ID,
                null,
                "Directory User",
                "directory@example.com",
                UserRole.EMPLOYEE);
        directoryUser.applyDirectoryAccess(true);
        when(invitations.findOpenByEmailForUpdate("directory@example.com"))
                .thenReturn(List.of(invitation(
                        ORGANIZATION_ID, "directory@example.com")));
        when(users.findByOrganizationIdAndEmailIgnoreCase(
                        ORGANIZATION_ID, "directory@example.com"))
                .thenReturn(Optional.of(directoryUser));

        assertTrue(service.provisionFromInvitation(
                        ISSUER, SUBJECT, "directory@example.com")
                .isEmpty());
        verify(identityBindings, never()).bind(any(), anyString(), anyString());
    }

    @Test
    void anAddressExpectedByTwoOrganizationsProvisionsNothing() {
        when(invitations.findOpenByEmailForUpdate("shared@example.com")).thenReturn(List.of(
                invitation(ORGANIZATION_ID, "shared@example.com"),
                invitation(OTHER_ORGANIZATION_ID, "shared@example.com")));

        assertTrue(service.provisionFromInvitation(ISSUER, SUBJECT, "shared@example.com").isEmpty());
        verify(users, never()).save(any());
        verify(identityBindings, never()).bind(any(), anyString(), anyString());
    }

    @Test
    void anExistingAccountIsLinkedRatherThanDuplicated() {
        var existing = new AppUser(ORGANIZATION_ID, DEPARTMENT_ID, "Linh", "linh@example.com", UserRole.EMPLOYEE);
        when(invitations.findOpenByEmailForUpdate("linh@example.com"))
                .thenReturn(List.of(invitation(ORGANIZATION_ID, "linh@example.com")));
        when(users.findByOrganizationIdAndEmailIgnoreCase(
                        ORGANIZATION_ID, "linh@example.com"))
                .thenReturn(Optional.of(existing));

        var provisioned = service.provisionFromInvitation(ISSUER, SUBJECT, "linh@example.com");

        assertEquals(existing.getId(), provisioned.orElseThrow().getId());
        verify(users, never()).save(any());
        verify(identityBindings).bind(eq(existing.getId()), eq(ISSUER), eq(SUBJECT));
    }

    @Test
    void anAcceptedInvitationIsClosedAndCannotBeUsedAgain() {
        var open = invitation(ORGANIZATION_ID, "newcomer@example.com");
        when(invitations.findOpenByEmailForUpdate("newcomer@example.com")).thenReturn(List.of(open));
        when(users.findByOrganizationIdAndEmailIgnoreCase(
                        ORGANIZATION_ID, "newcomer@example.com"))
                .thenReturn(Optional.empty());

        service.provisionFromInvitation(ISSUER, SUBJECT, "newcomer@example.com");

        assertFalse(open.open());
        assertEquals("ACCEPTED", open.getAcceptedAt() == null ? "OPEN" : "ACCEPTED");
    }

    @Test
    void aSignInWithoutAnAddressProvisionsNothing() {
        assertTrue(service.provisionFromInvitation(ISSUER, SUBJECT, null).isEmpty());
        assertTrue(service.provisionFromInvitation(ISSUER, SUBJECT, "  ").isEmpty());
        verify(invitations, never()).findOpenByEmailForUpdate(anyString());
    }

    @Test
    void inviteRejectsAnUnknownOrganization() {
        var failure = assertThrows(
                BusinessValidationException.class,
                () -> service.invite(
                        OTHER_ORGANIZATION_ID,
                        "newcomer@example.com",
                        null,
                        UserRole.EMPLOYEE,
                        INVITER_ID));

        assertEquals("invitation.organization-invalid", failure.code());
        verify(invitations, never()).save(any());
    }

    @Test
    void inviteRejectsADepartmentFromAnotherOrganization() {
        UUID otherDepartmentId = UUID.fromString("88888888-8888-4888-8888-888888888888");
        when(departments.existsByIdAndOrganizationId(otherDepartmentId, ORGANIZATION_ID))
                .thenReturn(false);

        var failure = assertThrows(
                BusinessValidationException.class,
                () -> service.invite(
                        ORGANIZATION_ID,
                        "newcomer@example.com",
                        otherDepartmentId,
                        UserRole.EMPLOYEE,
                        INVITER_ID));

        assertEquals("invitation.department-invalid", failure.code());
        verify(invitations, never()).save(any());
    }

    @Test
    void inviteRejectsAnInviterFromAnotherOrganization() {
        UUID otherInviterId = UUID.fromString("77777777-7777-4777-8777-777777777778");
        when(users.existsByIdAndOrganizationId(otherInviterId, ORGANIZATION_ID))
                .thenReturn(false);

        var failure = assertThrows(
                BusinessValidationException.class,
                () -> service.invite(
                        ORGANIZATION_ID,
                        "newcomer@example.com",
                        DEPARTMENT_ID,
                        UserRole.EMPLOYEE,
                        otherInviterId));

        assertEquals("invitation.inviter-invalid", failure.code());
        verify(invitations, never()).save(any());
    }

    @Test
    void invitePersistsOnlyAfterTenantReferencesAreVerified() {
        UserInvitation invitation = service.invite(
                ORGANIZATION_ID,
                "Newcomer@Example.com",
                DEPARTMENT_ID,
                UserRole.EMPLOYEE,
                INVITER_ID);

        assertEquals(ORGANIZATION_ID, invitation.getOrganizationId());
        assertEquals(DEPARTMENT_ID, invitation.getDepartmentId());
        assertEquals(INVITER_ID, invitation.getInvitedByUserId());
        assertEquals("newcomer@example.com", invitation.getEmail());
    }

    @Test
    void inviteRejectsAnExistingDirectoryManagedUser() {
        var directoryUser = new AppUser(
                ORGANIZATION_ID,
                null,
                "Directory User",
                "directory@example.com",
                UserRole.EMPLOYEE);
        directoryUser.applyDirectoryAccess(true);
        when(users.findByOrganizationIdAndEmailIgnoreCase(
                        ORGANIZATION_ID, "directory@example.com"))
                .thenReturn(Optional.of(directoryUser));

        var failure = assertThrows(
                BusinessConflictException.class,
                () -> service.invite(
                        ORGANIZATION_ID,
                        "Directory@Example.com",
                        null,
                        UserRole.EMPLOYEE,
                        INVITER_ID));

        assertEquals("invitation.user-scim-managed", failure.code());
        verify(invitations, never()).save(any());
    }

    @Test
    void directoryProvisioningAdoptsExistingUserAndConsumesInvitation() {
        var existing = new AppUser(
                ORGANIZATION_ID,
                DEPARTMENT_ID,
                "Existing Name",
                "employee@example.com",
                UserRole.MANAGER);
        var pending = invitation(ORGANIZATION_ID, "employee@example.com");
        when(invitations.findOpenByOrganizationIdAndEmailForUpdate(
                        ORGANIZATION_ID, "employee@example.com"))
                .thenReturn(Optional.of(pending));
        when(users.findByOrganizationIdAndEmailIgnoreCase(
                        ORGANIZATION_ID, "employee@example.com"))
                .thenReturn(Optional.of(existing));

        var result = service.provisionFromDirectory(
                new UserProvisioningService.DirectoryUserCommand(
                        ORGANIZATION_ID,
                        "Employee@Example.com",
                        "Directory Name",
                        true));

        assertEquals(existing.getId(), result.user().getId());
        assertTrue(result.adoptedExistingUser());
        assertEquals(pending.getId(), result.consumedInvitationId());
        assertFalse(pending.open());
        assertTrue(existing.isDirectoryManaged());
        assertEquals(UserRole.MANAGER, existing.getRole());
        assertEquals("Directory Name", existing.getName());
    }

    @Test
    void directoryProvisioningCreatesAnEmployeeWithoutInvitation() {
        when(invitations.findOpenByOrganizationIdAndEmailForUpdate(
                        ORGANIZATION_ID, "employee@example.com"))
                .thenReturn(Optional.empty());
        when(users.findByOrganizationIdAndEmailIgnoreCase(
                        ORGANIZATION_ID, "employee@example.com"))
                .thenReturn(Optional.empty());

        var result = service.provisionFromDirectory(
                new UserProvisioningService.DirectoryUserCommand(
                        ORGANIZATION_ID,
                        "Employee@Example.com",
                        null,
                        false));

        assertFalse(result.adoptedExistingUser());
        assertEquals("employee@example.com", result.user().getEmail());
        assertEquals("employee", result.user().getName());
        assertEquals(UserRole.EMPLOYEE, result.user().getRole());
        assertTrue(result.user().isDirectoryManaged());
        assertFalse(result.user().isActive());
    }

    @Test
    void directoryProvisioningRejectsABlankEmailWithADomainValidationError() {
        var failure = assertThrows(
                BusinessValidationException.class,
                () -> service.provisionFromDirectory(
                        new UserProvisioningService.DirectoryUserCommand(
                                ORGANIZATION_ID, "  ", "Directory User", true)));

        assertEquals("directory-user.email-required", failure.code());
        verify(users, never()).findByOrganizationIdAndEmailIgnoreCase(any(), anyString());
    }
}
