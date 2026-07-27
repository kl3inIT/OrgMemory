package com.orgmemory.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.ExternalIdentity;
import com.orgmemory.core.organization.ExternalIdentityRepository;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import com.orgmemory.core.organization.UserProvisioningService;
import com.orgmemory.core.organization.UserRole;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OidcCurrentActorProviderTests {

    private static final String ISSUER = "https://identity.example.test/realms/acme";

    /**
     * These cases are about the subject binding, not onboarding, so provisioning is stubbed to
     * find nothing. An address that nobody invited must leave the refusal exactly as it was.
     */
    private static UserProvisioningService refusingProvisioning() {
        UserProvisioningService provisioning = mock(UserProvisioningService.class);
        when(provisioning.provisionForVerifiedSignIn(any(), any(), any()))
                .thenReturn(Optional.empty());
        return provisioning;
    }

    @Test
    void resolvesOnlyTheExplicitIssuerSubjectBindingAndIgnoresJwtRolesAndEmail() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser user = linkedUser(identities, users, "stable-subject", true);

        var actor = new OidcCurrentActorProvider(identities, users, refusingProvisioning()).current(jwt(
                "stable-subject", "attacker@example.test", "ROLE_ADMIN"));

        assertEquals(user.getId(), actor.userId());
        assertEquals(user.getOrganizationId(), actor.organizationId());
        assertEquals("laura@acme.test", actor.email());
    }

    @Test
    void resolvesTheSameBindingForAnOidcBrowserSession() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        AppUser user = linkedUser(identities, users, "stable-subject", true);

        var actor = new OidcCurrentActorProvider(identities, users, refusingProvisioning()).current(oidcSession(
                "stable-subject", "attacker@example.test", "ROLE_ADMIN"));

        assertEquals(user.getId(), actor.userId());
        assertEquals(user.getOrganizationId(), actor.organizationId());
        assertEquals("laura@acme.test", actor.email());
    }

    @Test
    void rejectsVerifiedEmailAndAdminRoleWhenNoExplicitBindingExists() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        when(identities.findByIssuerAndSubject(ISSUER, "unknown-subject")).thenReturn(Optional.empty());

        var provider = new OidcCurrentActorProvider(identities, users, refusingProvisioning());
        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> provider.current(oidcSession(
                        "unknown-subject", "known-user@acme.test", "ROLE_ADMIN")));
    }

    @Test
    void rejectsAnInactiveLinkedUser() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        linkedUser(identities, users, "former-subject", false);

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> new OidcCurrentActorProvider(identities, users, refusingProvisioning()).current(jwt(
                        "former-subject", "former@acme.test", "ROLE_ADMIN")));
    }

    private static AppUser linkedUser(
            ExternalIdentityRepository identities,
            AppUserRepository users,
            String subject,
            boolean active) {
        AppUser user = new AppUser(
                UUID.randomUUID(),
                active ? UUID.randomUUID() : null,
                "Laura",
                "laura@acme.test",
                active ? UserRole.MANAGER : UserRole.EMPLOYEE,
                active);
        ExternalIdentity identity = new ExternalIdentity(user.getId(), ISSUER, subject);
        when(identities.findByIssuerAndSubject(ISSUER, subject)).thenReturn(Optional.of(identity));
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        return user;
    }

    @Test
    void anInvitedAddressIsProvisionedOnFirstSignInAndTheAddressNeverBecomesTheIdentity() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        UserProvisioningService provisioning = mock(UserProvisioningService.class);
        when(identities.findByIssuerAndSubject(ISSUER, "fresh-subject")).thenReturn(Optional.empty());
        AppUser invited = new AppUser(
                UUID.randomUUID(), null, "newcomer", "newcomer@example.test", UserRole.EMPLOYEE);
        when(provisioning.provisionForVerifiedSignIn(
                        ISSUER, "fresh-subject", "newcomer@example.test"))
                .thenReturn(Optional.of(invited));

        var actor = new OidcCurrentActorProvider(identities, users, provisioning)
                .current(jwt("fresh-subject", "newcomer@example.test", "ROLE_ADMIN"));

        assertEquals(invited.getId(), actor.userId());
        // The claim only chose the invitation; the binding written is against the subject, and
        // the role claimed in the token is still ignored.
        verify(provisioning).provisionForVerifiedSignIn(
                ISSUER, "fresh-subject", "newcomer@example.test");
    }

    @Test
    void anUnverifiedAddressCannotAcceptAnInvitation() {
        ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        UserProvisioningService provisioning = mock(UserProvisioningService.class);
        when(identities.findByIssuerAndSubject(ISSUER, "fresh-subject"))
                .thenReturn(Optional.empty());
        Jwt unverified = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer(ISSUER)
                .subject("fresh-subject")
                .claim("email", "newcomer@example.test")
                .claim("email_verified", false)
                .build();

        assertThrows(
                OrgMemoryAccessDeniedException.class,
                () -> new OidcCurrentActorProvider(
                                identities, users, provisioning)
                        .current(new JwtAuthenticationToken(unverified)));

        verify(provisioning, org.mockito.Mockito.never())
                .provisionForVerifiedSignIn(any(), any(), any());
    }

    private static JwtAuthenticationToken jwt(String subject, String email, String authority) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuer(ISSUER)
                .subject(subject)
                .claim("email", email)
                .claim("email_verified", true)
                .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));
    }

    private static OAuth2AuthenticationToken oidcSession(String subject, String email, String authority) {
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "test-id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of(
                        "iss", ISSUER,
                        "sub", subject,
                        "email", email,
                        "email_verified", true));
        var authorities = List.of(new SimpleGrantedAuthority(authority));
        DefaultOidcUser user = new DefaultOidcUser(authorities, idToken);
        return new OAuth2AuthenticationToken(user, authorities, "keycloak");
    }
}
