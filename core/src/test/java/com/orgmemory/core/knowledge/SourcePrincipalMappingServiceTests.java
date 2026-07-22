package com.orgmemory.core.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.ExternalIdentity;
import com.orgmemory.core.organization.ExternalIdentityRepository;
import com.orgmemory.core.permission.PermissionAuditService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SourcePrincipalMappingServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PRINCIPAL_ID = UUID.fromString("aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa");
    private static final UUID IDP_USER_ID = UUID.fromString("dddddddd-dddd-4ddd-dddd-dddddddddddd");
    private static final UUID EMAIL_USER_ID = UUID.fromString("eeeeeeee-eeee-4eee-eeee-eeeeeeeeeeee");
    private static final String ISSUER = "https://keycloak.local/realms/orgmemory";
    private static final String SUBJECT = "slack-subject-1";
    private static final String EMAIL = "an@example.com";

    private SourcePrincipalMappingRepository mappings;
    private AppUserRepository users;
    private ExternalIdentityRepository identities;
    private PermissionAuditService audit;
    private SourcePrincipalMappingService service;

    @BeforeEach
    void setUp() {
        mappings = mock(SourcePrincipalMappingRepository.class);
        users = mock(AppUserRepository.class);
        identities = mock(ExternalIdentityRepository.class);
        audit = mock(PermissionAuditService.class);
        service = new SourcePrincipalMappingService(mappings, users, identities, audit);
        when(mappings.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(mappings.findBySourcePrincipalIdAndStatus(PRINCIPAL_ID, SourcePrincipalMappingStatus.ACTIVE))
                .thenReturn(Optional.empty());
    }

    @Test
    void idpJoinTakesPrecedenceOverEmail() {
        SourcePrincipal principal = userPrincipal(true, EMAIL);
        AppUser idpUser = activeUser(ORGANIZATION_ID);
        when(identities.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(new ExternalIdentity(IDP_USER_ID, ISSUER, SUBJECT)));
        when(users.findById(IDP_USER_ID)).thenReturn(Optional.of(idpUser));

        Optional<SourcePrincipalMapping> mapping = service.autoMap(principal, ISSUER, SUBJECT);

        assertTrue(mapping.isPresent());
        assertEquals(SourcePrincipalMappingMethod.IDP_JOIN, mapping.get().getMethod());
        assertEquals(IDP_USER_ID, mapping.get().getAppUserId());
        verify(users, never()).findByEmailIgnoreCase(any());
        verify(audit).record(any());
    }

    @Test
    void emailJoinRequiresSsoVerified() {
        SourcePrincipal principal = userPrincipal(false, EMAIL);
        AppUser emailUser = activeUser(ORGANIZATION_ID);
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(emailUser));

        assertTrue(service.autoMap(principal, null, null).isEmpty());
        verify(mappings, never()).save(any());
        verify(audit, never()).record(any());
    }

    @Test
    void emailJoinBindsSsoVerifiedActiveUser() {
        SourcePrincipal principal = userPrincipal(true, EMAIL);
        AppUser user = activeUser(ORGANIZATION_ID);
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));

        Optional<SourcePrincipalMapping> mapping = service.autoMap(principal, null, null);

        assertTrue(mapping.isPresent());
        assertEquals(SourcePrincipalMappingMethod.SSO_EMAIL_JOIN, mapping.get().getMethod());
    }

    @Test
    void unmappedWhenNoTierMatches() {
        SourcePrincipal principal = userPrincipal(false, EMAIL);

        assertTrue(service.autoMap(principal, null, null).isEmpty());
        verify(mappings, never()).save(any());
    }

    @Test
    void inactiveInternalUserIsNotMapped() {
        SourcePrincipal principal = userPrincipal(true, EMAIL);
        AppUser idpUser = inactiveUser(ORGANIZATION_ID);
        when(identities.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(new ExternalIdentity(IDP_USER_ID, ISSUER, SUBJECT)));
        when(users.findById(IDP_USER_ID)).thenReturn(Optional.of(idpUser));

        assertTrue(service.autoMap(principal, ISSUER, SUBJECT).isEmpty());
        verify(mappings, never()).save(any());
    }

    @Test
    void groupPrincipalNeverMaps() {
        SourcePrincipal group = new SourcePrincipal(
                PRINCIPAL_ID, ORGANIZATION_ID, "slack", "T1", "C1",
                SourcePrincipalKind.SOURCE_GROUP, null, "general", false, Instant.now());

        assertTrue(service.autoMap(group, ISSUER, SUBJECT).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> service.selfClaim(group, IDP_USER_ID, "claim"));
    }

    @Test
    void selfClaimRejectsSecondActiveMappingToDifferentUser() {
        SourcePrincipal principal = userPrincipal(false, EMAIL);
        AppUser claimUser = activeUser(ORGANIZATION_ID);
        when(users.findById(EMAIL_USER_ID)).thenReturn(Optional.of(claimUser));
        SourcePrincipalMapping existing = new SourcePrincipalMapping(
                UUID.randomUUID(), ORGANIZATION_ID, PRINCIPAL_ID, IDP_USER_ID,
                SourcePrincipalMappingMethod.ADMIN_CONFIRMED, "prior", Instant.now());
        when(mappings.findBySourcePrincipalIdAndStatus(PRINCIPAL_ID, SourcePrincipalMappingStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        assertThrows(IllegalStateException.class,
                () -> service.selfClaim(principal, EMAIL_USER_ID, "claim"));
    }

    @Test
    void selfClaimIsIdempotentForSameUser() {
        SourcePrincipal principal = userPrincipal(false, EMAIL);
        AppUser claimUser = activeUser(ORGANIZATION_ID);
        when(users.findById(IDP_USER_ID)).thenReturn(Optional.of(claimUser));
        SourcePrincipalMapping existing = new SourcePrincipalMapping(
                UUID.randomUUID(), ORGANIZATION_ID, PRINCIPAL_ID, IDP_USER_ID,
                SourcePrincipalMappingMethod.SELF_CLAIM, "prior", Instant.now());
        when(mappings.findBySourcePrincipalIdAndStatus(PRINCIPAL_ID, SourcePrincipalMappingStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        assertSame(existing, service.selfClaim(principal, IDP_USER_ID, "claim"));
        verify(mappings, never()).save(any());
        verify(audit, never()).record(any());
    }

    private static SourcePrincipal userPrincipal(boolean ssoVerified, String email) {
        return new SourcePrincipal(
                PRINCIPAL_ID, ORGANIZATION_ID, "slack", "T1", "U1",
                SourcePrincipalKind.SOURCE_USER, email, "An", ssoVerified, Instant.now());
    }

    private static AppUser activeUser(UUID organizationId) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(EMAIL_USER_ID);
        when(user.isActive()).thenReturn(true);
        when(user.getOrganizationId()).thenReturn(organizationId);
        return user;
    }

    private static AppUser inactiveUser(UUID organizationId) {
        AppUser user = mock(AppUser.class);
        when(user.isActive()).thenReturn(false);
        when(user.getOrganizationId()).thenReturn(organizationId);
        return user;
    }
}
