package com.orgmemory.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private ExternalIdentityRepository identities;
    private UserInvitationRepository invitations;
    private UserProvisioningService service;

    @BeforeEach
    void setUp() {
        users = mock(AppUserRepository.class);
        identities = mock(ExternalIdentityRepository.class);
        invitations = mock(UserInvitationRepository.class);
        when(users.save(any())).thenAnswer(call -> call.getArgument(0));
        when(invitations.save(any())).thenAnswer(call -> call.getArgument(0));
        service = new UserProvisioningService(users, identities, invitations);
    }

    private UserInvitation invitation(UUID organizationId, String email) {
        return new UserInvitation(organizationId, email, DEPARTMENT_ID, UserRole.EMPLOYEE, INVITER_ID);
    }

    @Test
    void anInvitedAddressBecomesAUserBoundToItsSubject() {
        when(invitations.findOpenByEmail("newcomer@example.com"))
                .thenReturn(List.of(invitation(ORGANIZATION_ID, "newcomer@example.com")));
        when(users.findByEmailIgnoreCase("newcomer@example.com")).thenReturn(Optional.empty());

        var provisioned = service.provisionFromInvitation(ISSUER, SUBJECT, "Newcomer@Example.com");

        assertTrue(provisioned.isPresent());
        assertEquals("newcomer@example.com", provisioned.get().getEmail());
        assertEquals(ORGANIZATION_ID, provisioned.get().getOrganizationId());
        assertEquals(UserRole.EMPLOYEE, provisioned.get().getRole());
        verify(identities).linkIfAbsent(any(), eq(provisioned.get().getId()), eq(ISSUER), eq(SUBJECT));
    }

    @Test
    void anAddressWithNoInvitationProvisionsNothing() {
        when(invitations.findOpenByEmail(anyString())).thenReturn(List.of());

        assertTrue(service.provisionFromInvitation(ISSUER, SUBJECT, "stranger@example.com").isEmpty());
        verify(users, never()).save(any());
        verify(identities, never()).linkIfAbsent(any(), any(), anyString(), anyString());
    }

    @Test
    void anAddressExpectedByTwoOrganizationsProvisionsNothing() {
        when(invitations.findOpenByEmail("shared@example.com")).thenReturn(List.of(
                invitation(ORGANIZATION_ID, "shared@example.com"),
                invitation(OTHER_ORGANIZATION_ID, "shared@example.com")));

        assertTrue(service.provisionFromInvitation(ISSUER, SUBJECT, "shared@example.com").isEmpty());
        verify(users, never()).save(any());
        verify(identities, never()).linkIfAbsent(any(), any(), anyString(), anyString());
    }

    @Test
    void anExistingAccountIsLinkedRatherThanDuplicated() {
        var existing = new AppUser(ORGANIZATION_ID, DEPARTMENT_ID, "Linh", "linh@example.com", UserRole.EMPLOYEE);
        when(invitations.findOpenByEmail("linh@example.com"))
                .thenReturn(List.of(invitation(ORGANIZATION_ID, "linh@example.com")));
        when(users.findByEmailIgnoreCase("linh@example.com")).thenReturn(Optional.of(existing));

        var provisioned = service.provisionFromInvitation(ISSUER, SUBJECT, "linh@example.com");

        assertEquals(existing.getId(), provisioned.orElseThrow().getId());
        verify(users, never()).save(any());
        verify(identities).linkIfAbsent(any(), eq(existing.getId()), eq(ISSUER), eq(SUBJECT));
    }

    @Test
    void anAcceptedInvitationIsClosedAndCannotBeUsedAgain() {
        var open = invitation(ORGANIZATION_ID, "newcomer@example.com");
        when(invitations.findOpenByEmail("newcomer@example.com")).thenReturn(List.of(open));
        when(users.findByEmailIgnoreCase("newcomer@example.com")).thenReturn(Optional.empty());

        service.provisionFromInvitation(ISSUER, SUBJECT, "newcomer@example.com");

        assertFalse(open.open());
        assertEquals("ACCEPTED", open.getAcceptedAt() == null ? "OPEN" : "ACCEPTED");
    }

    @Test
    void aSignInWithoutAnAddressProvisionsNothing() {
        assertTrue(service.provisionFromInvitation(ISSUER, SUBJECT, null).isEmpty());
        assertTrue(service.provisionFromInvitation(ISSUER, SUBJECT, "  ").isEmpty());
        verify(invitations, never()).findOpenByEmail(anyString());
    }
}
